package controllers

import com.google.inject.Inject
import be.studiocredo.{VenueService, ShowService, EventService}
import be.studiocredo.auth.{Authorization, Secure, AuthenticatorService}
import play.api.mvc.Controller
import models.ids.{VenueId, ShowId, EventId}
import models.entities.FloorPlanJson
import play.api.libs.json.Json

class Events @Inject()(venueService: VenueService, eventService: EventService, showService: ShowService, val authService: AuthenticatorService) extends Controller with Secure {

  val defaultAuthorization = Some(Authorization.ANY)

  def view(id: EventId) = AuthAwareDBAction { implicit rs =>
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Event $id not found")
      case Some(details) => Ok(views.html.event(details, None))
    }
  }

  def viewShow(id: ShowId) = AuthAwareDBAction { implicit rs =>
    (for {
      show <- showService.get(id)
      details <- eventService.eventDetails(show.eventId)
    } yield {
      Ok(views.html.event(details, Some(show)))
    }).getOrElse(BadRequest(s"Could not find event/show $id"))
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
