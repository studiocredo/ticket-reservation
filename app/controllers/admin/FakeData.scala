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
import models.admin.MemberFormData
import models.admin.MemberFormData
import models.entities.ShowEdit
import models.entities.Row
import models.entities.EventEdit
import models.entities.UserDetailEdit
import models.entities.VenueEdit
import models.entities.FloorPlan
import models.entities.UserEdit
import models.entities.Spacer
import scala.Some
import models.entities.Seat

class FakeData @Inject()(memberService: MemberService,
                         userService: UserService,
                         venueService: VenueService,
                         eventService: EventService,
                         showService: ShowService) extends Controller {

  def insert() = DBAction { implicit rs =>
    val userAdmin = userService.insert(UserEdit("Admin", "admin", Passwords.hash("qsdfghjklm")), UserDetailEdit(Some("selckin@selckin.be"), None, None))
    userService.addRole(userAdmin, Roles.Admin)

    val thomas = memberService.insert(MemberFormData("Thomas", "thomas", Some("selckin@selckin.be"), None, None))
    val sven = memberService.insert(MemberFormData("sven", "sven",Some("sven@example.com"), None, None))
    val jantje = memberService.insert(MemberFormData("Jantje", "jantje", Some("selckin@selckin.be"), Some("veldstraat 20 gent"), Some("09/2345435453435")))

    val event1 = eventService.insert(EventEdit("xmas special", "", archived = false))
    val event2 = eventService.insert(EventEdit("Big show 2013", "", archived = false))

    val ven1 = venueService.insert(VenueEdit("Big room 1", "", archived = false))
    val ven2 = venueService.insert(VenueEdit("Big room 2", "", archived = false))
    val ven3 = venueService.insert(VenueEdit("Small room", "", archived = false))

    val NORM = Seat(SeatType.Normal)
    val VIP = Seat(SeatType.Vip)
    val DISA = Seat(SeatType.Disabled)

    venueService.update(ven1, FloorPlan(List(
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(Spacer(1), NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(Spacer(2), NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(Spacer(2), VIP, VIP, VIP, VIP, VIP, DISA)),
      Row(List(Spacer(4), VIP, VIP, VIP, VIP))
    )))
    venueService.update(ven2, FloorPlan(List(
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA))
    )))
    venueService.update(ven3, FloorPlan(List(
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA)),
      Row(List(NORM, NORM, NORM, NORM, NORM, NORM, NORM, DISA))
    )))

    showService.insert(ShowEdit(event1, ven1, new DateTime(2013, 12, 5, 17, 0), archived = false))
    showService.insert(ShowEdit(event1, ven1, new DateTime(2013, 12, 5, 19, 0), archived = false))
    showService.insert(ShowEdit(event1, ven1, new DateTime(2013, 12, 6, 17, 0), archived = false))
    showService.insert(ShowEdit(event1, ven1, new DateTime(2014, 2, 6, 19, 0), archived = false))
    showService.insert(ShowEdit(event1, ven2, new DateTime(2013, 12, 9, 19, 0), archived = false))

    showService.insert(ShowEdit(event2, ven2, new DateTime(2013, 12, 5, 17, 0), archived = false))
    showService.insert(ShowEdit(event2, ven3, new DateTime(2013, 12, 5, 19, 0), archived = false))
    showService.insert(ShowEdit(event2, ven1, new DateTime(2014, 4, 6, 17, 0), archived = false))
    showService.insert(ShowEdit(event2, ven1, new DateTime(2014, 5, 6, 19, 0), archived = false))

    Redirect(controllers.routes.Application.index())
  }
}

