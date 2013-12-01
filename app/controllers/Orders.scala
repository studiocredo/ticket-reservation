package controllers

import com.google.inject.Inject
import be.studiocredo.EventService
import be.studiocredo.auth.{Authorization, Secure, AuthenticatorService}
import play.api.mvc.Controller
import models.ids.{ShowId, EventId}

class Orders @Inject()(eventService: EventService, val authService: AuthenticatorService) extends Controller with Secure {

  val defaultAuthorization = Some(Authorization.ANY)


  def start(eventId: Option[Long], showId: Option[Long]) = AuthDBAction { implicit rs =>
      Ok(s"hi $eventId $showId" )
//    eventService.get(id) match {
//      case None => BadRequest(s"Event $id not found")
//      case Some(event) => Ok(views.html.order(event))
//    }
  }
}
