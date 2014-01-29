package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.{Authorization, Secure, AuthenticatorService}
import play.api.mvc.Controller
import models.ids.{VenueId, ShowId, EventId}
import models.entities.FloorPlanJson
import play.api.libs.json.Json
import scala.Some

class Events @Inject()(venueService: VenueService, eventService: EventService, showService: ShowService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService : UserService) extends Controller with Secure with UserContextSupport {

  val defaultAuthorization = Some(Authorization.ANY)

  def list(page: Int) = AuthAwareDBAction { implicit rs =>
    Ok(views.html.events(eventService.page(page), userContext))
  }

  def view(id: EventId) = AuthAwareDBAction { implicit rs =>
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Event $id not found")
      case Some(details) => Ok(views.html.event(details, None, userContext))
    }
  }

  def viewShow(eventId: EventId, showId: ShowId) = AuthAwareDBAction { implicit rs =>
    eventService.eventDetails(eventId) match {
      case None => BadRequest(s"Event $eventId not found")
      case Some(details) => Ok(views.html.event(details, details.shows.flatMap(_.shows).find(_.id == showId), userContext))
    }
  }

  import FloorPlanJson._

  def ajaxFloorplan(id: VenueId) = AuthAwareDBAction { implicit rs =>
    val plan = for {
      venue <- venueService.get(id)
      fp <- venue.floorplan
    } yield Ok(Json.toJson(fp))

    plan.getOrElse(BadRequest(s"Could not find floorplan for $id"))
  }

}
