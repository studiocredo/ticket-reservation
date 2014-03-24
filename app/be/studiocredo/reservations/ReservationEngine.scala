package be.studiocredo.reservations

import models.entities._
import models.entities.SeatStatus
import scala.collection.mutable
import models.entities.SeatType._
import models.entities.SeatType
import com.google.inject.Inject
import be.studiocredo.{ShowService, Service}
import akka.actor.Cancellable
import play.api.libs.concurrent.Akka
import play.api.Logger
import models.ids.ShowId
import play.api.db.slick._
import models.entities.FloorPlan
import models.entities.SeatId
import scala.Some
import models.entities.Seat
import models.entities.SeatWithStatus
import models.entities.Spacer
import models.entities.ShowAvailability

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

  def size: Int = partials.map(_.size).sum
  def score: Int = partials.map(_.score).sum + partials.length * SeparationPenalty // TODO + partials.slide(3).collect{seats.isOrphanSeat(_) => 1}.sum * OrphanSeatPenalty
  def seatIds: List[SeatId] = partials.map(_.seatIds).flatten
}

case class AvailableSeats(fp: FloorPlan, availability: ShowAvailability) {
  def get: List[RowContent] = fp.rows.foldLeft(Nil: List[RowContent])(_ ++ List(Spacer(100)) ++ _.content )
  
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

  def totalAvailable: Int = get.count(isAvailable(_))

  def takeBest(quantity: Int): List[SeatId] = {
    get.filter(isAvailable(_)).map(SeatScore.fromRowContent(_)).sortBy(_.score).take(quantity).map(_.seat)
  }

  def validate(suggestion: SeatSuggestion): Option[SeatSuggestion] = {
    val seatTypeMap = mutable.Map[SeatType, Int]()
    SeatType.values.foreach { st => seatTypeMap(st) = 0 }
    suggestion.seatIds.flatMap(sid => fp.seat(sid)).foreach(seat => seatTypeMap(seat.kind) += 1)
    if (SeatType.values.forall(st => seatTypeMap(st) <= availability.byType(st))) Some(suggestion) else None
  }
}

object ReservationEngine {
  def suggestSeats(quantity: Int, floorplan: FloorPlan, showAvailability: ShowAvailability, availableTypes: List[SeatType] = List(SeatType.Normal)): Either[String, List[SeatId]] = {
    val seats = AvailableSeats(floorplan, showAvailability)
    availableTypes.map(showAvailability.byType.get(_).getOrElse(0)).sum match {
      case total: Int if total >= quantity => {
        val groupSizesList = groupSizes(quantity)
        val adjacentSolutionsBySize = calculateAllAdjacentSuggestions(seats, groupSizesList.flatten.distinct.sorted.reverse)

        calculateBestSuggestion(groupSizesList.head.map(adjacentSolutionsBySize.getOrElse(_, mutable.Set()).toSet), seats) match {
          //if the first element produces a solution, then this is always considered the best (all on one row and next to each other)
          case Some(solution) => Right(solution.seatIds)
          case None => {
            groupSizesList.drop(1).map(sizes => calculateBestSuggestion(sizes.map(adjacentSolutionsBySize.getOrElse(_, mutable.Set()).toSet), seats)).flatten match {
              //nevermind, just take individual best seats
              case Nil => Right(seats.takeBest(quantity))
              case solutions => Right(solutions.minBy(_.score).seatIds)
            }
          }
        }
      }
      case _ => Left("re.capacity.insufficient")
    }
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
          val maxAdjacentAvailableCount = rowPart.takeWhile(seats.isAvailable(_)).length
          sizes.collect {
            case size if size <= maxAdjacentAvailableCount => map.addBinding(size, PartialSeatSuggestion(rowPart.take(size).map(SeatScore.fromRowContent(_))))
          }
      }
    }
    map
  }
}

case class ReservationEngine(show: ShowId) {

}

class ReservationEngineMonitorService @Inject()(showService: ShowService) extends Service {

  var cancellable: Option[Cancellable] = None
  val engines: mutable.Map[ShowId, ReservationEngine] = mutable.Map()

  def byShow(show: ShowId): Option[ReservationEngine] = engines.get(show)

  override def onStop() {
    cancellable.map(_.cancel())
  }

  override def onStart() {
    import play.api.Play.current
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    cancellable = Some(
      Akka.system.scheduler.schedule(0.seconds, 30.minutes) {
        val reservables = DB.withSession { implicit session: Session => showService.listReservable }
        (engines.keys.toSet -- reservables).foreach { sid =>
          engines.remove(sid)
        }
        (reservables.toSet -- engines.keys.toSet).foreach { sid =>
          engines.put(sid, ReservationEngine(sid))
        }
        if (Logger.isDebugEnabled) {
          Logger.debug(s"Registered reservation engines for show ids: ${engines.keys.mkString(",")}")
        }
      }
    )
  }
}