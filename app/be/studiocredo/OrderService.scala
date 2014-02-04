package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids
import models.entities._
import models.ids._
import models.entities.TicketSeatOrder
import models.entities.OrderEdit
import scala.collection.mutable

class OrderService {
  import models.schema.tables._

  val OQ = Query(Orders)
  val TOQ = Query(TicketOrders)
  val TSOQ = Query(TicketSeatOrders)

  def byShowId(id: ShowId)(implicit s: Session): List[TicketSeatOrder] = {
    TSOQ.where(_.showId === id).list
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

  //TODO validate if the seats from the seat order actually exist in the shows floorplan
  def insert(seatOrders: List[TicketSeatOrder])(implicit s: Session) = seatOrders.foreach { TicketSeatOrders.*.insert(_) }

  private def byId(id: ids.OrderId)= OQ.where(_.id === id)
}
