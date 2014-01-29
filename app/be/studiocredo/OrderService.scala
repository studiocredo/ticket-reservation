package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids
import models.entities._
import models.ids._
import models.entities.TicketSeatOrder
import models.entities.OrderEdit

class OrderService {
  import models.schema.tables._

  val OQ = Query(Orders)
  val TOQ = Query(TicketOrders)
  val TSOQ = Query(TicketSeatOrders)

  def byShowId(id: ShowId)(implicit s: Session): List[TicketSeatOrder] = {
    TSOQ.where(_.showId === id).list
  }

  def byUserId(id: UserId)(implicit s: Session): List[TicketSeatOrder] = {
    TSOQ.where(_.userId === id).list
  }

  def byUserIds(ids: List[UserId])(implicit s: Session): List[TicketSeatOrder] = {
    TSOQ.where(_.userId inSet ids).list
  }

  def insert(order: OrderEdit)(implicit s: Session): OrderId = Orders.autoInc.insert(order)
  def update(id: OrderId, billingName: String, billingAddress: String)(implicit s: Session) = byId(id).map(_.billingEdit).update((billingName, billingAddress))

  def insert(orderId: OrderId, showId: ShowId)(implicit s: Session): TicketOrderId = TicketOrders.autoInc.insert((orderId, showId))

  //TODO validate if the seats from the seat order actually exist in the shows floorplan
  def insert(seatOrders: List[TicketSeatOrder])(implicit s: Session) = seatOrders.foreach { TicketSeatOrders.*.insert(_) }

  private def byId(id: ids.OrderId)= OQ.where(_.id === id)
}
