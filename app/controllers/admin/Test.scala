package controllers.admin

import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import be.studiocredo._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService
import be.studiocredo.util.ServiceReturnValues._
import models.entities._
import scala.Some
import be.studiocredo.reservations.{AvailableSeats, ReservationEngine}
import models.entities.SeatWithStatus
import be.studiocredo.reservations.AvailableSeats
import models.entities.OrderEdit
import scala.Some
import be.studiocredo.util.Money
import scala.util.Random

class Test @Inject()(es: EventService, ss: ShowService, vs: VenueService, os: OrderService, prs: PreReservationService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  def suggest(quantity: Option[Int], showId: Option[ShowId] ) = AuthDBAction { implicit request =>

    val us = userService
    val userNames = List("u72", "u71")
    val users = userNames.map{us.findByUserName(_).get.id}

    val show = ss.get(showId.getOrElse(ShowId(1))).get
    val venue = vs.get(show.venueId).get
    val venueFloorplan = venue.floorplan.get

    val floorplan = vs.fillFloorplan(venueFloorplan, os.byShowId(show.id), users)
    val availability = prs.availability(ss.getEventShow(show.id), users)

    ReservationEngine.suggestSeats(quantity.getOrElse(6), floorplan, availability).fold(
      errmsg => Ok(errmsg),
      seats => { Ok(seats.map(_.name).mkString(", ")) }
    )
  }

  def reserve(quantity: Option[Int], showId: Option[ShowId] ) = AuthDBAction { implicit request =>
    val us = userService
    val userNames = List("admin")
    val users = userNames.map{us.findByUserName(_).get.id}

    val show = ss.get(showId.getOrElse(ShowId(1))).get
    val venue = vs.get(show.venueId).get
    val venueFloorplan = venue.floorplan.get

    val floorplan = vs.fillFloorplan(venueFloorplan, os.byShowId(show.id), users, List(SeatType.Disabled, SeatType.Normal, SeatType.Vip))

    val availability = prs.availability(ss.getEventShow(show.id), users)
    val seats = AvailableSeats(floorplan, availability)

    val random =  Random.shuffle(seats.get.filter(seats.isAvailable(_))).take(quantity.getOrElse(1)).collect{case seat:SeatWithStatus => seat.id}

    val u = us.find(users.head).get
    val orderId = os.insert(OrderEdit(u.id, org.joda.time.DateTime.now, u.name, u.address.getOrElse("n/a"), true))
    val ticketOrderId = os.insert(orderId, show.id)
    os.insert(random.map{ seat => TicketSeatOrder(ticketOrderId, show.id, Some(u.id), seat, Money(12.5)) })

    Ok(s"${random.map(_.name).mkString(",")}")
  }

  def prereserve(quantity: Option[Int], showId: Option[ShowId] ) = AuthDBAction { implicit request =>
    val us = userService
    val u = us.findByUserName("u1019").get
    val sid = showId.getOrElse(ShowId(1))
    val uid = u.id
    prs.findByShowAndUser(sid, uid) match {
      case None => prs.insert(ShowPrereservation(sid, uid, quantity.getOrElse(0)))
      case Some(showPrereservation) => prs.updateQuantity(ShowPrereservation(sid, uid, quantity.getOrElse(0) + showPrereservation.quantity))
    }
    Ok(s"ok")
  }

}
