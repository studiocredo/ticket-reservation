package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import scala.collection.mutable
import models.entities.SeatType._
import scala.Some
import com.google.inject.Inject
import be.studiocredo.util.ServiceReturnValues._

class OrderService @Inject()(venueService: VenueService) {
  import models.schema.tables._

  val OQ = Query(Orders)
  val TOQ = Query(TicketOrders)
  val TSOQ = Query(TicketSeatOrders)

  def byShowId(id: ShowId, excludedUsers: List[UserId] = List())(implicit s: Session): List[TicketSeatOrder] = {
    val query = for {
      tso <- TSOQ
      if tso.showId === id
    } yield (tso)
    query.list.filter{ tso =>
      tso.userId match {
        case Some(userId) => !excludedUsers.contains(userId)
        case None => true
      }
    }
  }

  def seatsByUser(id: UserId)(implicit s: Session): List[TicketSeatOrderDetail] = {
    seatsByUsers(List(id))
  }

  def seatsByUsers(ids: List[UserId])(implicit s: Session): List[TicketSeatOrderDetail] = {
    val query = for {
      (((ticketSeatOrder, show), event), user) <- TicketSeatOrders.leftJoin(Shows).on(_.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id).leftJoin(Users).on(_._1._1.userId === _.id)
      if user.id inSet ids
    } yield (ticketSeatOrder, show, event, user)
    query.list.map{ case (tso: TicketSeatOrder, s: Show, e: Event, u: User) => TicketSeatOrderDetail(tso, EventShow(s.id, e.id, e.name, s.venueId, s.date, s.archived), Some(u)) }
  }
  
  def detailedOrdersByUser(id: UserId)(implicit s: Session): List[OrderDetail] = {
    detailedOrdersByUsers(List(id))
  }

  //http://stackoverflow.com/questions/18147396/comparing-type-mapped-values-in-slick-queries
  //TODO
  def seatOrderExists(show: ShowId, seat: SeatId)(implicit s: Session): Boolean = {
    val query = for {
      tso <- TSOQ
      if tso.showId === show
//      if tso.seat === (seat:SeatId)  <- THIS DOESNT WORK
    } yield tso
    query.list.filter(tso => tso.seat == seat).headOption.isDefined
  }

  def detailedOrdersByUsers(ids: List[UserId])(implicit s: Session): List[OrderDetail] = {
    val query = for {
      (((((order, ticketOrder), ticketSeatOrder), show), event), user) <- Orders.leftJoin(TicketOrders).on(_.id === _.orderId).leftJoin(TicketSeatOrders).on(_._2.id === _.ticketOrderId).leftJoin(Shows).on(_._2.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id).leftJoin(Users).on(_._1._1._1._1.userId === _.id)
      if order.userId inSet ids
    } yield (order, ticketOrder, ticketSeatOrder, show, event, user)
    val orderMap = new mutable.HashMap[Order, mutable.Set[TicketOrder]]() with mutable.MultiMap[Order, TicketOrder]
    val ticketOrderMap = new mutable.HashMap[TicketOrder, mutable.Set[TicketSeatOrder]]() with mutable.MultiMap[TicketOrder, TicketSeatOrder]
    val showMap = mutable.Map[ShowId, EventShow]()
    val userMap = mutable.Map[UserId, User]()

    query.sortBy(_._1.date).list.foreach { case (order, ticketOrder, ticketSeatOrder, show, event, user) =>
      orderMap.addBinding(order, ticketOrder)
      ticketOrderMap.addBinding(ticketOrder, ticketSeatOrder)
      showMap.put(show.id, EventShow(show.id, event.id, event.name, show.venueId, show.date, show.archived))
      userMap.put(user.id, user)
    }

    orderMap.keys.map { order =>
      val ticketOrders = orderMap(order).toList.map{ ticketOrder =>
        val ticketSeatOrders = ticketOrderMap(ticketOrder).toList.map { ticketSeatOrder => TicketSeatOrderDetail(ticketSeatOrder, showMap(ticketSeatOrder.showId), ticketSeatOrder.userId match { case Some(userId) => Some(userMap(userId)); case _ => None }) }
        TicketOrderDetail(ticketOrder, order, showMap(ticketOrder.showId), ticketSeatOrders)
      }
      OrderDetail(order, userMap(order.userId), ticketOrders)
    }.toList
  }

  def insert(order: OrderEdit)(implicit s: Session): OrderId = Orders.autoInc.insert(order)
  def update(id: OrderId, billingName: String, billingAddress: String)(implicit s: Session) = byId(id).map(_.billingEdit).update((billingName, billingAddress))

  def insert(orderId: OrderId, showId: ShowId)(implicit s: Session): TicketOrderId = TicketOrders.autoInc.insert((orderId, showId))

  def insert(seatOrders: List[TicketSeatOrder])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    seatOrders.map{_.seat}

    //TODO proper call to venue service?
    val vQuery = for {
      (show, venue) <- Shows.leftJoin(Venues).on(_.venueId === _.id)
      if show.id inSet seatOrders.map{_.showId}
    } yield (show.id, venue)
    val venueMap = vQuery.list.toMap

    val badSeat = seatOrders.view.find {
      case so =>
        venueMap(so.showId).floorplan match {
          case Some(floorplan) => floorplan.seat(so.seat).isEmpty
          case None => true
        }
    }
    badSeat match {
      case Some(seatOrder) => Left(serviceFailure("reservations.seat.unknown", List(seatOrder.seat.name, venueMap(seatOrder.showId).name)))
      case None => {
        seatOrders.find { case so => seatOrderExists(so.showId, so.seat) } match {
          case Some(seatOrder) => Left(serviceFailure("reservation.seat.reserved", List(seatOrder.seat.name)))
          case None => {
            seatOrders.foreach { TicketSeatOrders.*.insert }
            Right(serviceSuccess("reservations.success"))
          }
        }
      }
    }
  }

  def capacity(show: EventShow, excludedUsers: List[UserId] = List())(implicit s: Session): ShowAvailability = {
    val venue = venueService.get(show.venueId).get
    val ticketSeatOrders = byShowId(show.id, excludedUsers)
    val floorplan = venue.floorplan.get

    val seatTypeMap = mutable.Map[SeatType, Int]()
    SeatType.values.foreach { st => seatTypeMap(st) = venue.capacityByType(st) }

    ticketSeatOrders.foreach { tso => floorplan.seat(tso.seat) match { case Some(seat) => seatTypeMap(seat.kind) -= 1; case None => }}
    ShowAvailability(show, seatTypeMap.toMap)
  }

  private def byId(id: ids.OrderId)= OQ.where(_.id === id)
}
