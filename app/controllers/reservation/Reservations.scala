package controllers.reservation

import models.ids._
import be.studiocredo._
import com.google.inject.Inject
import controllers.admin.AdminController
import be.studiocredo.auth.AuthenticatorService
import org.joda.time.format.{DateTimeFormatterBuilder, DateTimeFormat}
import java.util.Locale

class Reservations @Inject()(eventService: EventService,
                             val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with NotificationSupport {

  def create(id: EventId) = AuthAwareDBAction {
    implicit rs =>
      eventService.eventDetails(id) match {
        case None => BadRequest(s"Failed to retrieve details for event $id")
        case Some(details) => Ok(views.html.reservations.create(details, notifications))
      }
  }
}
