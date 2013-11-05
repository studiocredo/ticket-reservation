package controllers.admin

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo._
import play.api.Play.current
import models.entities._
import models.entities.VenueEdit
import models.entities.CourseEdit
import models.entities.GroupEdit
import models.entities.MemberEdit
import scala.Some
import org.joda.time.DateTime
import com.google.inject.Inject

class FakeData @Inject()(memberService: MemberService,
                         courseService: CourseService,
                         groupService: GroupsService,
                         venueService: VenueService,
                         eventService: EventService,
                         showService: ShowService) extends Controller {

  def insert() = DBAction { implicit rs =>
    /*
    val thomas = memberService.insert(MemberEdit("Thomas", Some("thomas@example.com"), None, None, archived = false))
    val sven = memberService.insert(MemberEdit("sven", Some("sven@example.com"), None, None, archived = false))
    val jantje = memberService.insert(MemberEdit("Jantje", None, Some("veldstraat 20 gent"), Some("09/2345435453435"), archived = false))

    val course1 = courseService.insert(CourseEdit("Indian rain dancing", archived = false))
    val course2 = courseService.insert(CourseEdit("Advanced roboting", archived = false))

    groupService.insert(GroupEdit("class A", 2010, course1, archived = false))
    val classA = groupService.insert(GroupEdit("class A", 2013, course1, archived = false))
    val classB = groupService.insert(GroupEdit("class B", 2013, course1, archived = false))
    val classD = groupService.insert(GroupEdit("class D", 2014, course2, archived = false))

    groupService.addMembers(classA, List(thomas, sven, jantje))
    groupService.addMembers(classB, List(thomas, jantje))
    groupService.addMembers(classD, List(jantje))

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
    */
    Redirect(controllers.routes.Application.index())
  }
}

