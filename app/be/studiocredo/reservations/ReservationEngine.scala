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


object SeatScore {
  def fromRowContent(rc: RowContent): SeatScore = rc match {
    case seat: Seat => SeatScore(seat.id, seat.preference)
    case seat: SeatWithStatus => SeatScore(seat.id, seat.preference)
    case _ => SeatScore(SeatId("none"), -1)
  }
}
case class SeatScore(seat: SeatId, score: Int)


case class PartialSeatSuggestion(seats: List[SeatScore]) {
  def size: Int = seats.length
  def score: Int = seats.map(_.score).sum
  def seatIds: List[SeatId] = seats.map(_.seat)
}

case class SeatSuggestion(partials: List[PartialSeatSuggestion]) {
  val SeparationPenalty = 20
  val OrphanSeatPenalty = 5

  val size: Int = partials.map(_.size).sum
  val score: Int = partials.map(_.score).sum + partials.length * SeparationPenalty // TODO + partials.slide(3).collect{seats.isOrphanSeat(_) => 1}.sum * OrphanSeatPenalty
  //TODO: if disabled seats are requested -> solutions that don't have this type get a penalty
  val seatIds: List[SeatId] = partials.map(_.seatIds).flatten
}

case class AvailableSeats(fp: FloorPlan, availabilityByType: Map[SeatType, Int]) {
  val get: List[RowContent] = fp.rows.foldLeft(Nil: List[RowContent])(_ ++ List(Spacer(100)) ++ _.content )
  
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

  def isOrphanSeat(left: RowContent, middle: RowContent, right: RowContent): Boolean = (left, middle, right) match {
    case (l, m, r) if isSeat(l) && !isAvailable(l) && isSeat(m) && isAvailable(m) && isSeat(r) && !isAvailable(r) => true
    case _ => false
  }

  def totalAvailable: Int = get.count(isAvailable)

  def takeBest(quantity: Int): List[SeatId] = {
    get.filter(isAvailable).map(SeatScore.fromRowContent).sortBy(_.score).take(quantity).map(_.seat)
  }

  def validate(suggestion: SeatSuggestion): Option[SeatSuggestion] = {
    val seatTypeMap = mutable.Map[SeatType, Int]()
    SeatType.values.foreach { st => seatTypeMap(st) = 0 }

    suggestion.seatIds.flatMap(sid => fp.seat(sid)).foreach(seat => seatTypeMap(seat.kind) += 1)

    if (SeatType.values.forall(st => seatTypeMap(st) <= availabilityByType.getOrElse(st, 0)))
      Some(suggestion)
    else
      None
  }
}

object ReservationEngine {
  val logger = Logger("be.studiocredo.suggester")
  // ShowAvailability = current free seats - prereserved seats

  def suggestSeats(quantity: Int, floorplan: FloorPlan, showAvailability: ShowAvailability, availableTypes: Set[SeatType] = Set(SeatType.Normal)): Either[String, List[SeatId]] = {
    suggestSeats(quantity, AvailableSeats(floorplan, showAvailability.byType), availableTypes)
  }

  def suggestSeats(quantity: Int, seats: AvailableSeats, availableTypes: Set[SeatType]): Either[String, List[SeatId]] = {
    val avail = availableTypes.map(seats.availabilityByType.get(_).getOrElse(0)).sum
    logger.debug(s"requested $quantity have $avail available $availableTypes ${seats.availabilityByType}")
    if (avail >= quantity) {
      val groupSizesList = groupSizes(quantity)
      val adjacentSolutionsBySize = calculateAllAdjacentSuggestions(seats, groupSizesList.flatten.distinct.sorted.reverse)

      def getSolutionForSize(size: Int) = adjacentSolutionsBySize.getOrElse(size, mutable.Set()).toSet

      // If first exists use it, else calc all others & use best scoring
      calculateBestSuggestion(groupSizesList.head.map(getSolutionForSize), seats) match {
        //if the first element produces a solution, then this is always considered the best (all on one row and next to each other)
        case Some(solution) => Right(solution.seatIds)
        case None => {
          groupSizesList.drop(1).map(sizes => calculateBestSuggestion(sizes.map(getSolutionForSize), seats)).flatten match {
            //nevermind, just take individual best seats
            case Nil => Right(seats.takeBest(quantity))
            case solutions =>
              Right(solutions.minBy(_.score).seatIds)
          }
        }
      }
    } else
      Left("re.capacity.insufficient")
  }


  private def groupSizes(size: Int): List[List[Int]] = {
    var result = List(List(size))
    if (size == 2) result = List(1, 1) :: result
    if (size > 8) result = List(size / 2, size - size / 2) :: result
    List(4, 3, 2).foreach(i => if (size > i) result = (size % i :: List.fill(size / i)(i)).reverse.filterNot(_ == 0) :: result)
    result.reverse
  }

  private def calculateBestSuggestion(solutions: List[Set[PartialSeatSuggestion]], seats: AvailableSeats): Option[SeatSuggestion] = solutions match {
    case Nil => None
    case head :: tail => tail.foldLeft(toSeatSuggestion(calculateBestAdjacentSuggestion(head), None, seats))((sg, psg) =>
      sg match {
        case None => None
        case Some(suggestion) => toSeatSuggestion(calculateBestAdjacentSuggestion(psg, suggestion.partials.map(_.seatIds).flatten), sg, seats)
      }

    )
  }

  private def toSeatSuggestion(psg: Option[PartialSeatSuggestion], sg: Option[SeatSuggestion], seats: AvailableSeats): Option[SeatSuggestion] = psg match {
    case None => None
    case Some(psg) => {
      sg match {
        case None => seats.validate(SeatSuggestion(List(psg)))
        case Some(sg) => seats.validate(SeatSuggestion(psg :: sg.partials))
      }
    }
  }

  private def calculateBestAdjacentSuggestion(suggestions: Set[PartialSeatSuggestion], taken: List[SeatId] = Nil): Option[PartialSeatSuggestion] = suggestions.toSeq match {
    case Seq() => None
    case _ => {
      suggestions.filter(x => taken.intersect(x.seatIds).isEmpty).toSeq match {
        case Seq() => None
        case suggestions => Some(suggestions.minBy(_.score))
      }
    }
  }

  //TODO this can be done faster with two pointer looping over the list
  private def calculateAllAdjacentSuggestions(seats: AvailableSeats, sizes: List[Int]): mutable.MultiMap[Int, PartialSeatSuggestion] = {
    val map = new mutable.HashMap[Int, mutable.Set[PartialSeatSuggestion]] with mutable.MultiMap[Int, PartialSeatSuggestion]
    if (!sizes.isEmpty) {
      val maxSize = sizes.max
      seats.get.sliding(maxSize).foreach {
        rowPart =>
          val maxAdjacentAvailableCount = rowPart.takeWhile(seats.isAvailable).length
          sizes.collect {
            case size if size <= maxAdjacentAvailableCount => map.addBinding(size, PartialSeatSuggestion(rowPart.take(size).map(SeatScore.fromRowContent)))
          }
      }
    }
    map
  }
}




class ReservationEngineMonitorService @Inject()(showService: ShowService, venueService: VenueService, orderService: OrderService, preReservationService: PreReservationService) extends Service {

  val logger = Logger("be.studiocredo.orders.seat")

  var seatOrderActor: Option[ActorRef] = None

  override def onStart() {
    import play.api.Play.current

    logger.debug("Starting reservation actor")

    seatOrderActor = Some(Akka.system.actorOf(SeatOrderActor.props(showService, venueService, orderService, preReservationService), name = "seatOrders"))
  }

  override def onStop() {
    logger.debug("Stopping reservation actor")
    seatOrderActor.map(Akka.system.stop)
    seatOrderActor = None
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

  case class AddSeat(show: ShowId, order: OrderId, seat: SeatId) extends FloorAction
  case class RemoveSeat(show: ShowId, order: OrderId, seat: SeatId) extends FloorAction

  case class CurrentStatus(show: ShowId, order: OrderId) extends FloorAction

  case class Commit(show: ShowId, order: OrderId, ticketOrderId: TicketOrderId) extends FloorAction
  case class ReloadState(show: ShowId) extends FloorAction



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

  case class Response(floorPlan: FloorPlan, timeout: Long, messages: List[Message])
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
  val TIMEOUT = Duration(10, TimeUnit.MINUTES)

  var timeout: Long = newTimeout

  def isOrder(id: OrderId) = id == this.orderId
  def isAvailable(seatType: SeatType) = availableTypes.contains(seatType)

  def touch() {
    this.timeout = newTimeout
  }

  def newTimeout = System.currentTimeMillis() + TIMEOUT.toMillis
}


object MaitreDActor {
  // has to be static
  def props(showId: ShowId, show: ShowService, venue: VenueService, order: OrderService, preReservation: PreReservationService) = Props({ new MaitreDActor(showId, show, venue, order, preReservation)})
}

// todo auth user access to order
class MaitreDActor(showId: ShowId, showService: ShowService, venueService: VenueService, orderService: OrderService, preReservationService: PreReservationService) extends Actor {
  val logger = Logger("be.studiocredo.maitred")

  import FloorProtocol._

  val orderInfoMap = mutable.Map[OrderId, OrderInfo]()

  override def preStart() {
    self ! ReloadState(showId)
  }

  override def receive = {
    case ReloadState(show) => context.become(running(loadState(show).get))
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
      Response(state.toFloorPlan(orderInfo), orderInfo.timeout, messages)
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
            avail(SeatType.Normal) -= Math.max(0, quantity - usedPreReservations(userId))
        }

        logger.debug(s"${avail}")
        AvailableSeats(state.toFloorPlan(order), avail.toMap)
      }
    }

    {
      case StartOrder(show, order, seats, users, price, availableTypes) => {
        orderInfoMap.remove(order)
        state.remove(order)

        val info = OrderInfo(order, availableTypes, users, price)

        val myAvailable = availableSeats(info)

        logger.debug(s"$seats requested hava ${myAvailable.totalAvailable} available ${myAvailable.availabilityByType}")
        if (myAvailable.totalAvailable < seats) {
          sender ! Status.Failure(new CapacityExceededException(show, order))
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
          val allSeats = state.findSeats(info)

          val current = seats.fold(allSeats)(wanted => allSeats.filter(seat => wanted.contains(seat.seatId)))

          state.adjacentFreeSeats(target, info, current.size).fold(msg => List(msg), suggestedSeats => {
            current foreach (_.setFree())
            suggestedSeats foreach (id => state(id).setPending(info))

            List()
          })
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

      case Commit(show, order, ticketOrderId) => {
        logger.debug(s"$show $order: commiting to $ticketOrderId")
        respond(order, info => {
          DB.withTransaction({ implicit session: Session =>
            case class UseablePreReservation(userId: UserId, var quantity: Int)

            val useablePreReservations = preReservationService.findForUsers(showId, info.users).map(pre => {
              val useable = Math.min(0, pre.quantity - state.countReservedSeats(pre.userId))
              UseablePreReservation(pre.userId, useable)
            }).sortBy(_.quantity)

            logger.debug(s"$show $order $ticketOrderId: useable reservations: $useablePreReservations")

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
              logger.debug(s"$show $order $ticketOrderId: seat->user" + seatsWithPreUser.map{case (seat, user) => (seat.seatId, user)})

            orderService.insert(seatsWithPreUser.map {
              case (seat, user) => {
                TicketSeatOrder(ticketOrderId, showId, user, seat.seatId, info.price)
              }
            })

            logger.debug(s"$show $order $ticketOrderId: order written")

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
          
          context.become(running(newState))
          Status.Success(null)
        })
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
        case reserved:Reserved => SeatStatus.Reserved
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
case class CapacityExceededException(show :ShowId, orderId: OrderId) extends RuntimeException()

case class ReloadFailedException(show: ShowId) extends RuntimeException()

case class NotInitializedException(show: ShowId) extends RuntimeException()
case class MissingOrderException(orderId: OrderId) extends RuntimeException()
case class FailedLoadExistingSeatOrderException(show: ShowId) extends RuntimeException()
case class ShowNotAcceptingOrdersException(show: ShowId) extends RuntimeException()
