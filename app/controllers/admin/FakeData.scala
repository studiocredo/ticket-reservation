package controllers.admin

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo._
import play.api.Play.current
import models.entities._
import models.entities.VenueEdit
import scala.Some
import org.joda.time.DateTime
import com.google.inject.Inject
import be.studiocredo.auth.{Roles, Passwords}
import models.admin.MemberFormData

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

