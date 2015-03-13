package be.studiocredo.reservations

import scala.collection.mutable
import models.entities.SeatType._
import com.google.inject.Inject
import akka.actor._
import akka.actor.Status
import akka.actor.Status.Status
import play.api.db.slick._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import models.ids.{TicketOrderId, UserId, OrderId, ShowId}
import models.entities
import models.entities._
import be.studiocredo._
import be.studiocredo.reservations.SeatState.{SeatState, FloorplanState}
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import play.api.Logger
import be.studiocredo.reservations.FloorProtocol.{ErrorMessage, Message}
import models.entities.SeatId
import models.entities.SeatWithStatus
import models.entities.Row
import models.entities.ShowAvailability
import models.entities.Spacer
import models.entities.FloorPlan
import models.entities.Seat
import be.studiocredo.util.Money
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import controllers.auth.Mailer
import scala.collection.mutable.ListBuffer
import models.admin.EventReservationsDetail


object SeatScore {
  def fromRowContent(rc: RowContent): SeatScore = rc match {
    case seat: Seat => SeatScore(seat.id, seat.preference)
    case seat: SeatWithStatus => SeatScore(seat.id, seat.preference)
    case _ => SeatScore(SeatId("none"), -1)
  }
}
case class SeatScore(seat: SeatId, score: Int)
case class SuggestionScore(partialScore: Int, separationScore: Int, orphanScore: Int, missingSeatTypeScore: Int) {
  val value = partialScore + separationScore + orphanScore + missingSeatTypeScore
  override def toString() = s"P$partialScore S$separationScore O$orphanScore M$missingSeatTypeScore T$value"
}

case class PartialSeatSuggestion(seats: List[SeatScore]) {
  def size: Int = seats.length
  def score: Int = seats.map(_.score).sum
  def seatIds: List[SeatId] = seats.map(_.seat)
}

case class SeatSuggestion(partials: List[PartialSeatSuggestion], initialSeats: AvailableSeats, availableTypes: Set[SeatType]) {
  val SeparationPenalty = 10
  val OrphanSeatPenalty = 20
  val MissingSeatTypePenalty = 100

  val size: Int = partials.map(_.size).sum
  val seatIds: List[SeatId] = partials.map(_.seatIds).flatten
  val seats = initialSeats.getWithSelected(seatIds)

  val score = {
    import AvailableSeats._
    val partialScore = partials.map(_.score).sum
    val separationScore = (partials.length-1) * SeparationPenalty
    val orphanScore = seats.sliding(3).count{case l :: m :: r :: Nil => isOrphanSeat(l,m,r); case _ => false}*OrphanSeatPenalty

    val typesInSuggestion = seats.collect{case seat: Seat if seatIds.contains(seat.id) => seat.kind; case seat: SeatWithStatus if seatIds.contains(seat.id) => seat.kind}.distinct
    val missingSeatTypeScore = (availableTypes - SeatType.Normal -- typesInSuggestion).size*MissingSeatTypePenalty
    SuggestionScore(partialScore, separationScore, orphanScore, missingSeatTypeScore)
  }
}

object AvailableSeats {
  def isAvailable(rc: RowContent): Boolean = rc match {
    case seat: Seat => true
    case seat: SeatWithStatus if seat.status == SeatStatus.Free => true
    case _ => false
  }

  def isSeat(rc: RowContent): Boolean = rc match {
    case seat: Seat => true
    case seat: SeatWithStatus => true
    case _ => false
  }

  def toSeatType (rc: RowContent): Option[SeatType] = rc match {
    case seat: Seat => Some(seat.kind)
    case seat: SeatWithStatus => Some(seat.kind)
    case _ => None
  }

  def isOrphanSeat(left: RowContent, middle: RowContent, right: RowContent): Boolean = (left, middle, right) match {
    case (l, m, r) if isSeat(l) && !isAvailable(l) && isSeat(m) && isAvailable(m) && isSeat(r) && !isAvailable(r) => true
    case _ => false
  }
}

case class AvailableSeats(fp: FloorPlan, availabilityByType: Map[SeatType, Int]) {
  import AvailableSeats._
  val get: List[RowContent] = fp.rows.foldLeft(Nil: List[RowContent])(_ ++ List(Spacer(100)) ++ _.content )

  val totalFree: Int = get.count(isAvailable)

  def totalAvailable(types: Set[SeatType]): Int = {
    types.map(availabilityByType.get(_).getOrElse(0)).sum
  }

  def takeBest(quantity: Int): List[SeatId] = {
    get.filter(isAvailable).map(SeatScore.fromRowContent).sortBy(_.score).take(quantity).map(_.seat)
  }

  def validateCapacityByType(suggestion: SeatSuggestion): Boolean = {
    val seatTypeMap = mutable.Map[SeatType, Int]()
    SeatType.values.foreach { st => seatTypeMap(st) = 0 }

    suggestion.seatIds.flatMap(sid => fp.seat(sid)).foreach(seat => seatTypeMap(seat.kind) += 1)

    SeatType.values.forall(st => seatTypeMap(st) <= availabilityByType.getOrElse(st, 0))
  }

  def getWithSelected(selected: List[SeatId]): List[RowContent] = {
    get.map {
      case seat: SeatWithStatus if selected.contains(seat.id) => SeatWithStatus(seat.id, seat.kind, SeatStatus.Mine, seat.preference, seat.comment)
      case rc: RowContent => rc
    }
  }
}

object ReservationEngine {
  val logger = Logger("be.studiocredo.suggester")
  // ShowAvailability = current free seats - prereserved seats

  def suggestSeats(quantity: Int, floorplan: FloorPlan, showAvailability: ShowAvailability, availableTypes: Set[SeatType] = Set(SeatType.Normal)): Either[String, List[SeatId]] = {
    suggestSeats(quantity, AvailableSeats(floorplan, showAvailability.byType), availableTypes)
  }

  def suggestSeats(quantity: Int, seats: AvailableSeats, availableTypes: Set[SeatType]): Either[String, List[SeatId]] = {
    val start = System.nanoTime()
    try {
      if (quantity <= 0 || quantity > EventReservationsDetail.maxQuantityPerReservation) //check for max is only a quick check, more robust checking requires user info and order info but that is an expensive calculation
        return Left("re.quantity.invalid")
      val avail = seats.totalAvailable(availableTypes)
      logger.debug(s"requested $quantity have $avail available $availableTypes ${seats.availabilityByType}")
      if (avail >= quantity) {
        val groupSizesList = groupSizes(quantity)
        val adjacentSolutionsBySize = calculateAllAdjacentSuggestions(seats, groupSizesList.flatten.distinct.sorted.reverse)

        def getSolutionForSize(size: Int) = adjacentSolutionsBySize.getOrElse(size, Nil)

        // If first exists use it, else calc all others & use best scoring
        calculateBestSuggestion(groupSizesList.head.map(getSolutionForSize), seats, availableTypes) match {
          //if the first element produces a solution, then this is always considered the best (all on one row and next to each other)
          case Some(solution) => {
            logger.debug(s"Best suggestion for adjacent seats (${groupSizesList.head.mkString(",")}) -> [${solution.seatIds.map(_.name).mkString(",")}}] score=${solution.score}")
            Right(solution.seatIds)
          }
          case None => {
            groupSizesList.drop(1).map(sizes => calculateBestSuggestion(sizes.map(getSolutionForSize), seats, availableTypes)).flatten match {
              //nevermind, just take individual best seats
              case Nil => {
                val solution = seats.takeBest(quantity)
                logger.debug(s"Suggestion for individual best seats -> [${solution.map(_.name).mkString(",")}}]")
                Right(solution)
              }
              case solutions => {
                val bestSolution = solutions.minBy(_.score.value)
                logger.debug(s"Best suggestion for multiple adjacent seats (${groupSizesList.head.mkString(",")}) -> [${bestSolution.seatIds.map(_.name).mkString(",")}}] score=${bestSolution.score}")
                Right(bestSolution.seatIds)
              }
            }
          }
        }
      } else
        Left("re.capacity.insufficient")
    } finally {
      logger.debug(s"Suggestion took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)} ms")
    }
  }


  private def groupSizes(size: Int): List[List[Int]] = {
    var result = List(List(size))
    if (size == 2) result = List(1, 1) :: result
    if (size > 8) result = List(size / 2, size - size / 2) :: result
    List(4, 3, 2).foreach(i => if (size > i) result = (size % i :: List.fill(size / i)(i)).reverse.filterNot(_ == 0) :: result)
    result.reverse
  }

  private def calculateBestSuggestion(solutions: List[List[PartialSeatSuggestion]], seats: AvailableSeats, availableTypes: Set[SeatType]): Option[SeatSuggestion] = solutions match {
    case Nil => None
    case head :: tail => tail.foldLeft(addBestPartialSuggestion(head, None, seats, availableTypes))((sg, psg) =>
      sg match {
        case None => None
        case Some(suggestion) => addBestPartialSuggestion(psg, Some(suggestion), seats, availableTypes)
      }
    )
  }

  private def addBestPartialSuggestion(psgs: List[PartialSeatSuggestion], sg: Option[SeatSuggestion], seats: AvailableSeats, availableTypes: Set[SeatType]): Option[SeatSuggestion] = {
    val taken = sg.map(_.seatIds).getOrElse(Nil)
    val sgs = psgs.filter(filterNotTaken(taken)).map(toSeatSuggestion(sg, seats, availableTypes)).filter(filterValid(seats))
    sgs match {
      case Nil => None
      case _ => Some(sgs.minBy(_.score.value))
    }
  }

  private def toSeatSuggestion(sg: Option[SeatSuggestion], seats: AvailableSeats, availableTypes: Set[SeatType]) = (psg: PartialSeatSuggestion) => {
    sg match {
      case None => SeatSuggestion(List(psg), seats, availableTypes)
      case Some(sg) => SeatSuggestion(psg :: sg.partials, seats, availableTypes)
    }
  }

  private def filterValid(seats: AvailableSeats) = (sg: SeatSuggestion) => {
    seats.validateCapacityByType(sg)
  }

  private def filterNotTaken(taken: List[SeatId] = Nil) = (psg: PartialSeatSuggestion) => {
    taken.intersect(psg.seatIds).isEmpty
  }

  private def calculateAllAdjacentSuggestions(seats: AvailableSeats, sizes: List[Int]): Map[Int, List[PartialSeatSuggestion]] = {
    import AvailableSeats._
    val result = sizes.map((_, new ListBuffer[PartialSeatSuggestion])).toMap
    if (!sizes.isEmpty) {
      val it = seats.get.iterator
      while (it.hasNext) {
        val adjacentAvailableSeats = it.dropWhile(!isAvailable(_)).takeWhile(isAvailable)
        sizes.foreach{ size =>
          adjacentAvailableSeats.sliding(size).withPartial(false).foreach{ rcList =>
            result(size) += PartialSeatSuggestion(rcList.map(SeatScore.fromRowContent).toList)
          }
        }
      }
    }
    result.map{ case (key, value) => (key, value.toList) }.toMap
  }
}




class ReservationEngineMonitorService @Inject()(showService: ShowService, venueService: VenueService, orderService: OrderService, preReservationService: PreReservationService, userService: UserService) extends Service {

  val logger = Logger("be.studiocredo.orders.seat")

  var seatOrderActor: Option[ActorRef] = None
  var cancellable: Option[Cancellable] = None

  override def onStart() {
    import play.api.Play.current
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    logger.debug("Starting reservation actor")

    seatOrderActor = Some(Akka.system.actorOf(SeatOrderActor.props(showService, venueService, orderService, preReservationService), name = "seatOrders"))

    cancellable = Some(
      Akka.system.scheduler.schedule(1.hour, 6.hours) {
        logger.info("Closing stale open orders")
        DB.withSession {
          implicit session: Session =>
            orderService.closeStale().map {order =>
              Mailer.sendOrderConfirmationEmail(userService.find(order.user.id).get,order)
              logger.warn(s"Closed stale open order ${order.id} (created ${order.order.date} and notified owner ${order.user.name} (${order.user.id})")
            }
        }
      }
    )
  }

  override def onStop() {
    logger.debug("Stopping reservation actor")
    seatOrderActor.map(Akka.system.stop)
    seatOrderActor = None
    cancellable.map(_.cancel())
    cancellable = None
  }

  def floors: ActorRef = {
    if (seatOrderActor.isDefined)
      seatOrderActor.get
    else
      throw new IllegalStateException()
  }
}

object FloorProtocol {

  val DEFAULT_TYPES = Set(SeatType.Normal)

  trait FloorAction {
    def show: ShowId

  }

  case class StartOrder(show: ShowId, order: OrderId, seats: Int, users: List[UserId], price: Money, availableTypes: Set[SeatType] = DEFAULT_TYPES) extends FloorAction

  case class MoveBest(show: ShowId, order: OrderId) extends FloorAction
  case class Move(show: ShowId, order: OrderId, target: SeatId, seats: Option[Set[SeatId]]) extends FloorAction
  case class Cancel(show: ShowId, order: OrderId) extends FloorAction

  case class AddSeat(show: ShowId, order: OrderId, seat: SeatId) extends FloorAction
  case class RemoveSeat(show: ShowId, order: OrderId, seat: SeatId) extends FloorAction

  case class CurrentStatus(show: ShowId, order: OrderId) extends FloorAction

  case class Commit(show: ShowId, order: OrderId) extends FloorAction
  case class ReloadState(show: ShowId) extends FloorAction
  case class ReloadFullState()


  case object TimeOut

  trait Message {
    def text: String
    def isError: Boolean = false
    def isInfo: Boolean = false
  }
  case class InfoMessage(text: String) extends Message {
    override def isInfo = true
  }
  case class ErrorMessage(text: String) extends Message {
    override def isError= true
  }

  case class Response(floorPlan: FloorPlan, timeout: Long, seq:Long, messages: List[Message])
}

object SeatOrderActor {
  // has to be static
  def props(showService: ShowService, venueService: VenueService, orderService: OrderService, preReservation: PreReservationService) = Props({new SeatOrderActor(showService, venueService, orderService, preReservation)})
}

class SeatOrderActor(showService: ShowService, venueService: VenueService, orderService: OrderService, preReservationService: PreReservationService) extends Actor {
  import FloorProtocol._
  val logger = Logger("be.studiocredo.orders.seat")

  case class SetActiveShows(active: Set[ShowId])

  var activeShows: Set[ShowId] = Set()
  var showPaths: Map[ShowId, ActorSelection] = Map.empty
  var cancellable: Option[Cancellable] = None

  override def preStart() {
    import play.api.Play.current
    import scala.concurrent.duration._
    import context.dispatcher
    cancellable = Some(
      context.system.scheduler.schedule(0.seconds, 30.minutes) {
        logger.info("Updating active shows")
        DB.withSession {
          implicit session: Session =>
            self ! SetActiveShows(showService.listActive)
        }
      }
    )
  }

  override def postStop() = {
    cancellable.map(_.cancel())
  }

  override def receive = {
    case SetActiveShows(showIds) => {
      logger.info(s"Active shows: $showIds")
      activeShows = showIds
      activeShows foreach getShowRef
    }

    case reload: ReloadFullState => {
      logger.info("Reloading state of all active shows")
      for (show <- activeShows) {
        getShowRef(show).tell(ReloadState(show), sender)
      }
    }


    case action: FloorAction => {
      if (!activeShows.contains(action.show))
        sender ! Status.Failure(new ShowNotAcceptingOrdersException(action.show))
      getShowRef(action.show).tell(action, sender)
    }

    case other => logger.warn(s"Unhandled message $other")
  }

  def getShowRef(showId: ShowId): ActorRef = {
    val id = showId.id.toString
    context.child(id).getOrElse({
      context.actorOf(MaitreDActor.props(showId, showService, venueService, orderService, preReservationService), id)
    })

  }
}

case class OrderInfo(orderId: OrderId, availableTypes: Set[SeatType], users: List[UserId], price: Money) {
  def isOrder(id: OrderId) = id == this.orderId
  def isAvailable(seatType: SeatType) = availableTypes.contains(seatType)


  val TIMEOUT = Duration(3, TimeUnit.MINUTES)
  var timeout: Long = newTimeout
  def touch() =  this.timeout = newTimeout

  def newTimeout = System.currentTimeMillis() + TIMEOUT.toMillis
  def isTimedOut(current: Long): Boolean = this.timeout < current
}


object MaitreDActor {
  val MAGIC_SEQ_START = System.nanoTime()
  // has to be static
  def props(showId: ShowId, show: ShowService, venue: VenueService, order: OrderService, preReservation: PreReservationService) = Props({ new MaitreDActor(showId, show, venue, order, preReservation)})
}

class MaitreDActor(showId: ShowId, showService: ShowService, venueService: VenueService, orderService: OrderService, preReservationService: PreReservationService) extends Actor {
  val logger = Logger("be.studiocredo.maitred")

  import FloorProtocol._

  var seq = System.nanoTime() - MaitreDActor.MAGIC_SEQ_START  // stay within js range
  val orderInfoMap = mutable.Map[OrderId, OrderInfo]()

  var cancellable: Option[Cancellable] = None

  override def preStart() {
    self ! ReloadState(showId)

    import scala.concurrent.duration._
    import context.dispatcher
    cancellable = Some(context.system.scheduler.schedule(1.minutes, 1.minutes, self, TimeOut))
  }

  override def postStop() = {
    cancellable.map(_.cancel())
  }

  override def receive = {
    case ReloadState(show) => context.become(running(loadState(show).get), true)
    case TimeOut =>
    case other => sender ! Status.Failure(new NotInitializedException(showId))
  }

  def running(state: FloorplanState): Receive = {
    def respond(orderId: OrderId, body: (OrderInfo => List[Message])) = {
      orderInfoMap.get(orderId).fold(
        sender ! Status.Failure(new MissingOrderException(orderId))
      )(
          info => {
            sender ! toResponse(info, body(info))
          })
    }

    def error(msg: String): Message = ErrorMessage(msg)

    def toResponse(orderInfo: OrderInfo, messages: List[Message] = List()) = {
      seq+=1
      logger.debug(s"Update message sequence number to $seq")
      Response(state.toFloorPlan(orderInfo), orderInfo.timeout, seq, messages)
    }

    def availableSeats(order: OrderInfo)= {
      val avail = mutable.Map[SeatType, Int]().withDefaultValue(0) ++ state.seatCountbyType
      // Subtract seats that are reserved or pending for other pepole
      val takenSeatsExcludingMyOwn: (SeatState) => Boolean = seat => !(seat.isFree || seat.isPending(order))
      state.bySeat.values.filter(takenSeatsExcludingMyOwn).foreach(seat => avail(seat.kind) -= 1)

      DB.withSession { implicit session: Session =>
        val usedPreReservations = state.reservedSeatsByUser()
        preReservationService.preReservationsByShow(showId, order.users).foreach {
          case (userId, quantity) =>
            val unusedPreReservations = quantity - usedPreReservations(userId)
            //require (unusedPreReservations >= 0, {logger.error(s"Invalid unused $unusedPreReservations for user $userId")})
            deductUnusedPreReservations(List(SeatType.Normal, SeatType.Disabled), avail, unusedPreReservations)
        }

        logger.debug(s"Available seats = ${avail}")
        AvailableSeats(state.toFloorPlan(order), avail.toMap)
      }
    }

    def deductUnusedPreReservations(seatTypes: List[SeatType.Value], avail: mutable.Map[SeatType, Int], quantity: Int) = {
      val remainder = seatTypes.foldLeft(quantity) { (q, seatType) =>
        val toDeduct = Math.min(avail(seatType), quantity)
        avail(seatType) -= toDeduct
        toDeduct
      }
      //require (remainder == 0, {logger.error(s"Could not deduct $quantity prereservations from availability $avail: event overbooked")})
    }

    {
      case StartOrder(show, order, seats, users, price, availableTypes) => {
        orderInfoMap.remove(order)
        state.remove(order)

        val info = OrderInfo(order, availableTypes, users, price)

        val myAvailable = availableSeats(info)
        val totalAvailable = myAvailable.totalAvailable(availableTypes)

        logger.debug(s"$seats ${availableTypes.mkString(",")} requested have $totalAvailable available ${myAvailable.availabilityByType}")
        if (totalAvailable < seats) {
          sender ! Status.Failure(new CapacityExceededException(show, order, totalAvailable))
        } else {
          val suggested = ReservationEngine.suggestSeats(seats, myAvailable, info.availableTypes)

          sender ! suggested.fold(msg => {
            Status.Failure(new FailedToAssignInitialSeatingException(msg))
          }, newSeats => {

            orderInfoMap += ((order, OrderInfo(order, availableTypes, users, price)))
            newSeats foreach (id => state(id).setPending(info))

            toResponse(info, List())
          })
        }
      }

      case MoveBest(show, order) => {
        respond(order, info => {
          info.touch()

          val old = state.findSeats(info)
          val suggested = ReservationEngine.suggestSeats(old.size, availableSeats(info), info.availableTypes)
          suggested.fold(msg => List(error(msg)), newSeats => {
            old foreach (_.setFree())
            newSeats foreach (id =>
              state(id).setPending(info)
             )

            List[Message]()
          })
        })
      }

      case Move(show, order, target, seats) => {
        respond(order, info => {
          info.touch()

          val allSeats = state.findSeats(info)

          val current = seats.fold(allSeats)(wanted => allSeats.filter(seat => wanted.contains(seat.seatId)))

          state.adjacentFreeSeats(target, info, current.size).fold(msg => List(msg), suggestedSeats => {
            current foreach (_.setFree())
            suggestedSeats foreach (id => state(id).setPending(info))

            List()
          })
        })
      }

      case Cancel(show, order) => {
        respond(order, info => {
          state.findSeats(info) foreach(_.setFree())
          List()
        })
      }

      case AddSeat(show, order, seat) => {
        respond(order, info => {
          val ss = state(seat)
          if (ss.isFree) {
            ss.setPending(info)
            List()
          } else {
            List(error("seat.notavailable"))
          }
        })
      }
      case RemoveSeat(show, order, seat) => {
        respond(order, info => {
          val ss = state(seat)
          if (ss.isPending(order)) {
            ss.setFree()
            List()
          } else {
            List(error("seat.notyours"))
          }
        })
      }

      case CurrentStatus(show, order) => {
        respond(order, info => List())
      }

      case Commit(show, order) => {
        logger.debug(s"$show $order: commiting")
        respond(order, info => {
          info.touch()

          DB.withTransaction({ implicit session: Session =>
            case class UseablePreReservation(userId: UserId, var quantity: Int)

            val useablePreReservations = preReservationService.findForUsers(showId, info.users).map(pre => {
              val useable = Math.max(0, pre.quantity - state.countReservedSeats(pre.userId))
              UseablePreReservation(pre.userId, useable)
            }).sortBy(_.quantity)

            logger.debug(s"$show $order: useable reservations: $useablePreReservations")

            def nextPreReservation(): Option[UserId] = {
              val maybePre = useablePreReservations.find(pre => pre.quantity > 0)
              if (maybePre.isEmpty)
                None
              else {
                val next = maybePre.get
                next.quantity -= 1
                Some(next.userId)
              }
            }

            val seatsWithPreUser = state.findSeats(info).map(seat => (seat, nextPreReservation()))

            if (logger.isDebugEnabled)
              logger.debug(s"$show $order: seat->user" + seatsWithPreUser.map{case (seat, user) => (seat.seatId, user)})

            val ticketOrderId = orderService.insert(order, showId)(session)

            orderService.insert(seatsWithPreUser.map {
              case (seat, user) => {
                TicketSeatOrder(ticketOrderId, showId, user, seat.seatId, info.price)
              }
            })

            logger.debug(s"$show $order order written: $ticketOrderId")

            seatsWithPreUser.foreach{case (seat, user) => seat.setReserved(user)}
          })
          List()
        })
      }
      case ReloadState(show) => {
        sender ! loadState(showId).fold(
          Status.Failure(new ReloadFailedException(showId)): Status
        )(newState => {
          newState.drainPending(state)
          
          context.become(running(newState), true)
          Status.Success(null)
        })
      }

      case TimeOut => {
        val current = System.currentTimeMillis()
        orderInfoMap.values.toList foreach { info =>
          if (info.isTimedOut(current)) {
            logger.debug(s"$showId ${info.orderId}: Timeout, removing seats")
            state.findSeats(info) foreach (seat => seat.setFree())
            orderInfoMap.remove(info.orderId)
          }
        }
      }
    }
  }


  def loadState(show: ShowId): Option[FloorplanState] =  {
    DB.withSession {
      implicit session: Session =>
         for (
          s <- showService.get(showId);
          v <- venueService.get(s.venueId);
          floorPlan <- v.floorplan
        ) yield {
          val state = SeatState.create(floorPlan)

          for (reserved <- orderService.byShowId(show)) {
            require(reserved.showId == show)

            state(reserved.seat).setReserved(reserved.userId)
          }

          state
        }
    }
  }
}

object SeatState {

  sealed trait Status
  case class Reserved(preReservation: Option[UserId]) extends Status // takes prereservation
  case object Free extends Status
  case class Pending(order: OrderInfo) extends Status

  class SeatState(val seat: Seat, var status: Status) extends RowContentState {
    def remove(id: OrderId) {
      if (isPending(id))
        setFree()
    }

    def seatId = seat.id

    def setReserved(userId: Option[UserId]) = status = Reserved(userId)
    def setPending(info: OrderInfo) = status = Pending(info)
    def setFree() = status = Free

    def isPending(info: OrderInfo): Boolean = isPending(info.orderId)
    def isPending(id: OrderId): Boolean = status match {
      case pending:Pending => pending.order.isOrder(id)
      case _ => false
    }

    def isFree: Boolean = status match {
      case Free => true
      case _ => false
    }
    def isReserved = status match {
      case reserved: Reserved => true
      case _ => false
    }
    def isReserved(userId: UserId) = status match {
        case Reserved(user) => user.isDefined && user.get == userId
        case _ => false
      }


    def seatIsAvailableFor(info :OrderInfo): Boolean = (isFree || isPending(info)) && info.availableTypes.contains(seat.kind)

    override def toContent(implicit order: OrderInfo): RowContent = {

      val planStatus = status match {
        case reserved:Reserved => SeatStatus.Unavailable
        case Free =>  if (order.isAvailable(seat.kind)) SeatStatus.Free else SeatStatus.Unavailable
        case Pending(info) => if (order == info) SeatStatus.Mine else SeatStatus.Reserved
      }

      entities.SeatWithStatus(seat.id, seat.kind, planStatus, seat.preference, None)
    }

    def kind = seat.kind
  }
  class UnhandledState(content: RowContent) extends RowContentState {
    override def toContent(implicit order: OrderInfo): RowContent = content
  }
  trait RowContentState {
    def toContent(implicit order: OrderInfo): RowContent
  }

  class RowState(row: Row, val content: List[RowContentState]) {
    def toRow(implicit order: OrderInfo): Row =  Row(content.map(_.toContent), row.vspace)
  }

  class FloorplanState(rows: List[RowState], val bySeat: Map[SeatId, SeatState]) {
    val seatCountbyType: Map[SeatType, Int] = bySeat.values.groupBy(_.kind).mapValues(_.size).toMap

    val seatList = rows.foldLeft(Nil: List[RowContentState])(_ ++ List(new UnhandledState(Spacer(100))) ++ _.content )

    def findSeats(info: OrderInfo): List[SeatState] =  bySeat.values.filter(_.isPending(info.orderId)).toList

    def remove(id: OrderId) {
      bySeat.values.map(_.remove(id))
    }

    def drainPending(other: FloorplanState) {
      bySeat.values.filter(_.isFree).foreach(state => {
        other.bySeat.get(state.seatId).foreach(oldState => {
          state.status = oldState.status match {
            case pending: Pending => pending
            case _ => state.status
          }
        })
      })
    }

    def adjacentFreeSeats(target: SeatId, order: OrderInfo, quantity: Int): Either[Message, List[SeatId]] = {
      if (quantity <= 0 || quantity > EventReservationsDetail.maxQuantityPerReservation) //check for max is only a quick check, more robust checking requires user info and order info but that is an expensive calculation
        return Left(ErrorMessage("re.quantity.invalid"))
      val targetIdx = seatList.indexWhere({
        case state:SeatState => state.seatId == target
        case _ => false
      })
      if (targetIdx < 0) {
        return Left(ErrorMessage("re.suggest.invalidtarget"))
      }

      val suggested = mutable.ArrayBuffer[SeatId]()
      def addSeatIfAvailable(index: Int) = seatList(index) match {
        case state: SeatState if state.seatIsAvailableFor(order) => suggested += state.seatId
        case _ =>
      }

      var current = targetIdx
      while (current < seatList.size && suggested.size < quantity) {
        addSeatIfAvailable(current)
        current += 1
      }

      current = targetIdx - 1
      while (current >= 0 && suggested.size < quantity) {
        addSeatIfAvailable(current)
        current -= 1
      }

      if (suggested.size == quantity)
        Right(suggested.toList)
      else
        Left(ErrorMessage("re.suggest.notenoughseats")) // Should be impossible
    }

    def countReservedSeats(userId: UserId): Int =  bySeat.values.count(_.isReserved(userId))
    def reservedSeatsByUser(): mutable.Map[UserId, Int] = {
      val usedPreReservations = mutable.Map[UserId, Int]().withDefaultValue(0)
      for (state <- bySeat.values) {
        state.status match  {
          case Reserved(user) => {
            if (user.isDefined)
              usedPreReservations(user.get) += 1
          }
          case _ =>
        }
      }
      usedPreReservations
    }

    def apply(seatId: SeatId) = bySeat(seatId)

    def toFloorPlan(implicit order: OrderInfo): FloorPlan = FloorPlan(rows.map(_.toRow))
  }

  def empty = new FloorplanState(List(), Map())

  def create(floorPlan: FloorPlan) = {
    val bySeat = mutable.Map[SeatId, SeatState]()

    new FloorplanState(floorPlan.rows.map(
      row => {
        new RowState(row, row.content.map({
          case seat: Seat => {
            val state = new SeatState(seat, Free)
            bySeat += ((seat.id, state))
            state
          }
          case other => new UnhandledState(other)
        }))
      }
    ), bySeat.toMap)
  }
}


case class FailedToAssignInitialSeatingException(msg: String) extends RuntimeException(msg)
case class CapacityExceededException(show :ShowId, orderId: OrderId, remaining: Int) extends RuntimeException()

case class ReloadFailedException(show: ShowId) extends RuntimeException()

case class NotInitializedException(show: ShowId) extends RuntimeException()
case class MissingOrderException(orderId: OrderId) extends RuntimeException()
case class FailedLoadExistingSeatOrderException(show: ShowId) extends RuntimeException()
case class ShowNotAcceptingOrdersException(show: ShowId) extends RuntimeException()
