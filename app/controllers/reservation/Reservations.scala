package controllers.reservation

import models.ids._
import be.studiocredo._
import com.google.inject.Inject
import controllers.admin.AdminController
import be.studiocredo.auth.AuthenticatorService
import org.joda.time.format.{DateTimeFormatterBuilder, DateTimeFormat}
import java.util.Locale

class Reservations @Inject()(eventService: EventService,
                             val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  def create(id: EventId) = AuthAwareDBAction {
    implicit rs =>
      eventService.eventDetails(id) match {
        case None => BadRequest(s"Evenement $id niet gevonden")
        case Some(details) => Ok(views.html.reservations.create(details, userContext))
      }
  }
}
