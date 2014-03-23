package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import com.google.inject.Inject
import models.entities.SeatStatus.SeatStatus
import models.entities.SeatStatus
import be.studiocredo.util.ServiceReturnValues._
import models.entities.TicketSeatOrder
import models.entities.VenueEdit
import models.entities.FloorPlan
import models.Page
import models.entities.SeatId
import scala.Some
import models.entities.Seat
import models.entities.SeatWithStatus
import models.entities.Row
import models.entities.Venue
import models.entities.Spacer
import models.entities.SeatType.SeatType
import models.entities.SeatType

class VenueService @Inject()() {

  import models.queries._
  import models.schema.tables._

  val VenuesQ = Query(Venues)
  val active = VenuesQ.filter(_.archived === false)


  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Venue] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active, page, pageSize)
    } {
      (query, filter) => query.filter(q => iLike(q.name, filter)) // should replace with lucene
    }.run
    Page(values, page, pageSize, offset, total)
  }

  def list()(implicit s: Session) = active.list

  def insert(venue: VenueEdit)(implicit s: Session): VenueId = Venues.autoInc.insert(venue)
  def update(id: VenueId, venue: VenueEdit)(implicit s: Session) = editById(id).update(venue)

  def update(id: VenueId, floorPlan: FloorPlan)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateFloorPlan(floorPlan).fold(
      error => Left(error),
      success => {
        byId(id).map(_.floorplan).update(Some(floorPlan))
        Right(success)
      }
    )
  }

  private def validateFloorPlan(floorPlan: FloorPlan): Either[ServiceFailure, ServiceSuccess] = {
    val rowContent = floorPlan.rows.map{_.content}.flatten
    val seats = rowContent.collect{case seat:Seat => seat}
    if (rowContent.exists(_ match { case seat:SeatWithStatus => true; case _ => false})) {
      Left(serviceFailure("floorplan.invalid.type"))
    } else if (seats.size != seats.toSet.size) {
      val dups: Iterable[String] = seats.groupBy{_.id.name}.filter{case (_,lst) => lst.size > 1 }.keys
      Left(serviceFailure("floorplan.invalid.notunique", dups.toList))
    } else if (seats.exists(_.preference <= 0)) {
      Left(serviceFailure("floorplan.invalid.preference.notpositive"))
    } else {
      Right(serviceSuccess("floorplan.save.success"))
    }
  }

  def get(id: VenueId)(implicit s: Session): Option[Venue] = byId(id).firstOption
  def getEdit(id: VenueId)(implicit s: Session): Option[VenueEdit] = editById(id).firstOption

  def delete(id: VenueId)(implicit s: Session) = {
    (for (v <- VenuesQ if v.id === id) yield v.archived).update(true)

  }

  def fillFloorplan(fp: FloorPlan, tickets: List[TicketSeatOrder], users: List[UserId] = List(), availableTypes: List[SeatType] = List(SeatType.Normal)): FloorPlan = {
   val (mine, notmine) = tickets.partition(ticket => ticket.userId match { case Some(userId) => users.contains(userId); case None => false})
   FloorPlan(fp.rows.map{row => Row(row.content.map{ case seat: Seat => SeatWithStatus(seat.id, seat.kind, getSeatStatus(seat, mine.map{_.seat}, notmine.map{_.seat}, users, availableTypes), seat.preference); case seat:SeatWithStatus => seat; case spacer:Spacer => spacer }, row.vspace) })
  }

  def fillFloorplanDetailed(fp: FloorPlan, tickets: List[TicketSeatOrderDetail], users: List[UserId] = List(), availableTypes: List[SeatType] = List(SeatType.Normal)): FloorPlan = {
    val (mine, notmine) = tickets.partition(ticket => ticket.ticketSeatOrder.userId match { case Some(userId) => users.contains(userId); case None => false})
    FloorPlan(fp.rows.map{row => Row(row.content.map{ case seat: Seat => SeatWithStatus(seat.id, seat.kind, getSeatStatus(seat, mine.map{_.ticketSeatOrder.seat}, notmine.map{_.ticketSeatOrder.seat}, users, availableTypes), seat.preference, getComment(seat, tickets)); case seat:SeatWithStatus => seat; case spacer:Spacer => spacer }, row.vspace) })
  }

  private def getComment(seat: Seat, tickets: List[TicketSeatOrderDetail]): Option[String] = {
    tickets.find(_.ticketSeatOrder.seat == seat.id) match {
      case None => None
      case Some(ticket) => Some(s"${ticket.user.map(_.name).getOrElse("Onbekend")}")
    }
  }

  private def getSeatStatus(seat: Seat, mine: List[SeatId], notmine: List[SeatId], users: List[UserId], availableTypes: List[SeatType]): SeatStatus = {
    if (mine.contains(seat.id)) {
      SeatStatus.Mine
    } else if (!availableTypes.contains(seat.kind)) {
      SeatStatus.Unavailable
    } else if (notmine.contains(seat.id)) {
      SeatStatus.Reserved
    } else {
      SeatStatus.Free
    }
  }

  private def byId(id: ids.VenueId)=  VenuesQ.where(_.id === id)
  private def editById(id: ids.VenueId) = byId(id).map(_.edit)
}
