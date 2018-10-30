import java.io.{File, FileInputStream}

import be.studiocredo.auth.Passwords
import be.studiocredo.reservations.TicketGeneratorLike
import be.studiocredo.util.Money
import models.entities._
import models.ids.{EventId, OrderId, ShowId, TicketOrderId, UserId, VenueId}
import org.joda.time.DateTime

val template = Some("conf/templates/Unplugged.pdf")
val user = User(UserId(123L), "Test User", "test", Passwords.hash("test"), None, active = true)
val order = Order(OrderId(655L), user.id, DateTime.now(), "Test Naam", "Teststraat 99, 8000 Brugge", processed = false, archived = false, Some("Automatisch aangemaakt door test script"))
val show = EventShow(ShowId(997L), EventId(44L), "Test show evenement", VenueId(184L), "Test locatie", DateTime.now(), template, archived = false)
val ticketOrder = TicketOrder(TicketOrderId(56L), order.id, show.id)
val ticketSeatOrder = TicketSeatOrder(TicketOrderId(935L), show.id, Some(user.id), SeatId("A45"), Money(45.32))
val ticketSeatOrderDetail = TicketSeatOrderDetail(ticketSeatOrder, show)
val ticketOrderDetail = TicketOrderDetail(ticketOrder, order, show, List(ticketSeatOrderDetail))
val detail = OrderDetail(order, user, List(ticketOrderDetail))
val ticket = TicketDistribution(order.id, 1, DateTime.now())
val url = "http://www.studiocredo.be/test"

object TestTicketGenerator extends TicketGeneratorLike {
  override def getTicketInputStreams(eventShows: List[EventShow]) = Map(show.eventId -> template.map{new FileInputStream(_)})
}

val document = TestTicketGenerator.create(detail, ticket, url).get
document.saveAs(new File("test.pdf"))