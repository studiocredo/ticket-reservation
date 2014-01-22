package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids
import models.entities.TicketSeatOrder

class OrderService {
  import models.queries._
  import models.schema.tables._

  val TSOQ = Query(TicketSeatOrders)

  def byShowId(id: ids.ShowId)(implicit s: Session): List[TicketSeatOrder] = {
    TSOQ.where(_.showId === id).list
  }
}
