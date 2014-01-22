package controllers.admin

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo._
import play.api.Play.current
import models.entities._
import scala.Some
import org.joda.time.DateTime
import com.google.inject.Inject
import be.studiocredo.auth.{Roles, Passwords}
import models.admin.{ShowEdit, UserFormData}
import models.entities.Row
import models.entities.EventEdit
import models.entities.UserDetailEdit
import models.entities.VenueEdit
import models.entities.FloorPlan
import models.entities.UserEdit
import models.entities.Spacer
import scala.Some
import models.entities.Seat

class FakeData @Inject()(userService: UserService,
                         venueService: VenueService,
                         eventService: EventService,
                         showService: ShowService) extends Controller {

  def insert() = DBAction { implicit rs =>
    val userAdmin = userService.insert(UserEdit("Admin", "admin", Passwords.hash("qsdfghjklm")), UserDetailEdit(Some("selckin@selckin.be"), None, None))
    userService.addRole(userAdmin, Roles.Admin)

    val thomas = userService.insert(UserFormData("Thomas", "thomas", Some("selckin@selckin.be"), None, None))
    val sven = userService.insert(UserFormData("sven", "sven",Some("sven@example.com"), None, None))
    val jantje = userService.insert(UserFormData("Jantje", "jantje", Some("selckin@selckin.be"), Some("veldstraat 20 gent"), Some("09/2345435453435")))

    val event1 = eventService.insert(EventEdit("xmas special", "", preReservationStart = Some(new DateTime(2013, 12, 9, 0 ,0)), preReservationEnd = Some(new DateTime(2013, 12, 20, 0, 0)), reservationStart = Some(new DateTime(2013, 12, 21, 0 ,0)), reservationEnd = Some(new DateTime(2013, 12, 24, 0 ,0)), archived = false))
    val event2 = eventService.insert(EventEdit("Big show 2013", "", preReservationStart = Some(new DateTime(2013, 12, 9, 0 ,0)), preReservationEnd = Some(new DateTime(2013, 12, 20, 0, 0)), reservationStart = Some(new DateTime(2013, 12, 21, 0 ,0)), reservationEnd = Some(new DateTime(2013, 12, 24, 0 ,0)), archived = false))

    val ven1 = venueService.insert(VenueEdit("Big room 1", "", archived = false))
    val ven2 = venueService.insert(VenueEdit("Big room 2", "", archived = false))
    val ven3 = venueService.insert(VenueEdit("Small room", "", archived = false))

    def norm(name: String): Seat = { Seat(SeatId(name), SeatType.Normal) }
    def vip(name: String): Seat = { Seat(SeatId(name), SeatType.Vip) }
    def disa(name: String): Seat = { Seat(SeatId(name), SeatType.Disabled) }

    venueService.update(ven1, FloorPlan(List(
      Row(List(norm("A1"), norm("A2"), norm("A3"), norm("A4"), norm("A5"), norm("A6"), norm("A7"), disa("A8")), 0),
      Row(List(norm("B1"), norm("B2"), norm("B3"), norm("B4"), norm("B5"), norm("B6"), norm("B7"), disa("B8")), 0),
      Row(List(norm("C1"), norm("C2"), norm("C3"), norm("C4"), norm("C5"), norm("C6"), norm("C7"), disa("C8")), 0),
      Row(List(norm("D1"), norm("D2"), norm("D3"), norm("D4"), norm("D5"), norm("D6"), norm("D7"), disa("D8")), 0),
      Row(List(norm("E1"), norm("E2"), norm("E3"), norm("E4"), norm("E5"), norm("E6"), norm("E7"), disa("E8")), 0),
      Row(List(Spacer(1), norm("F1"), norm("F2"), norm("F3"), norm("F4"), norm("F5"), norm("F6"), disa("F7")), 1),
      Row(List(Spacer(2), norm("G1"), norm("G2"), norm("G3"), norm("G4"), norm("G5"), disa("G6")), 0),
      Row(List(Spacer(2), vip("H1"), vip("H2"), vip("H3"), vip("H4"), vip("H5"), disa("H6")), 0),
      Row(List(Spacer(4), vip("I1"), vip("I2"), vip("I3"), vip("I4")), 0)
    )))
    venueService.update(ven2, FloorPlan(List(
      Row(List(norm("D1"), norm("D2"), norm("D3"), norm("D4"), norm("D5"), norm("D6"), norm("D7"), disa("D8")), 0),
      Row(List(norm("C1"), norm("C2"), norm("C3"), norm("C4"), norm("C5"), norm("C6"), norm("C7"), disa("C8")), 0),
      Row(List(norm("B1"), norm("B2"), norm("B3"), norm("B4"), norm("B5"), norm("B6"), norm("B7"), disa("B8")), 1),
      Row(List(norm("A1"), norm("A2"), norm("A3"), norm("A4"), norm("A5"), norm("A6"), norm("A7"), disa("A8")), 0)
    )))
    venueService.update(ven3, FloorPlan(List(
      Row(List(norm("D1"), norm("D2"), norm("D3"), norm("D4"), norm("D5"), norm("D6"), norm("D7"), disa("D8")), 0),
      Row(List(norm("C1"), norm("C2"), norm("C3"), norm("C4"), norm("C5"), norm("C6"), norm("C7"), disa("C8")), 0),
      Row(List(norm("B1"), norm("B2"), norm("B3"), norm("B4"), norm("B5"), norm("B6"), norm("B7"), disa("B8")), 0),
      Row(List(norm("A1"), norm("A2"), norm("A3"), norm("A4"), norm("A5"), norm("A6"), norm("A7"), disa("A8")), 0)
    )))

    showService.insert(event1, ShowEdit(ven1, new DateTime(2013, 12, 5, 17, 0)))
    showService.insert(event1, ShowEdit(ven1, new DateTime(2013, 12, 5, 19, 0)))
    showService.insert(event1, ShowEdit(ven1, new DateTime(2013, 12, 6, 17, 0)))
    showService.insert(event1, ShowEdit(ven1, new DateTime(2014, 2, 6, 19, 0)))
    showService.insert(event1, ShowEdit(ven2, new DateTime(2013, 12, 9, 19, 0)))

    showService.insert(event2, ShowEdit(ven2, new DateTime(2013, 12, 5, 17, 0)))
    showService.insert(event2, ShowEdit(ven3, new DateTime(2013, 12, 5, 19, 0)))
    showService.insert(event2, ShowEdit(ven1, new DateTime(2014, 4, 6, 17, 0)))
    showService.insert(event2, ShowEdit(ven1, new DateTime(2014, 5, 6, 19, 0)))

    Redirect(controllers.routes.Application.index())
  }
}

