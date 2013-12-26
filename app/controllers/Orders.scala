package controllers

import com.google.inject.Inject
import be.studiocredo.EventService
import be.studiocredo.auth._
import play.api.mvc.{Result, Controller}
import models.ids.{ShowId, EventId}
import scala.Some

class Orders @Inject()(eventService: EventService, val authService: AuthenticatorService) extends Controller with Secure {

  val defaultAuthorization = Some(Authorization.ANY)

  def start(eventId: Option[EventId], showId: Option[ShowId]) = AuthDBAction { implicit rs =>
    eventId match {
      case None => Redirect(routes.Application.index())
      case Some(id) => startEvent(EventId(id), showId.map(ShowId(_)))
    }
  }

  def startEvent(eventId: EventId, showId: Option[ShowId])(implicit rs: SecuredDBRequest[_]): Result = {
    eventService.eventDetails(eventId) match {
      case None => BadRequest(s"Event $eventId not found")
      case Some(event) => Ok(views.html.order(event, showId))
    }
  }
}
