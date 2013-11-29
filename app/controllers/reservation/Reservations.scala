package controllers.reservation

import models.ids._
import be.studiocredo._
import com.google.inject.Inject
import controllers.admin.AdminController
import be.studiocredo.auth.AuthenticatorService
import org.joda.time.format.{DateTimeFormatterBuilder, DateTimeFormat}
import java.util.Locale

class Reservations @Inject()(eventService: EventService,
                             val authService: AuthenticatorService) extends AdminController {

  implicit val dateFormatter = new DateTimeFormatterBuilder()
    .appendDayOfWeekText()
    .appendLiteral(' ')
    .appendDayOfMonth(1)
    .appendLiteral(' ')
    .appendMonthOfYearText()
    .appendLiteral(' ')
    .appendYear(4,4)
    .appendLiteral(" om ")
    .appendHourOfDay(1)
    .appendLiteral('u')
    .appendMinuteOfHour(2)
    .toFormatter()
    .withLocale(Locale.forLanguageTag("nl"));

  def create(id: EventId) = AuthAwareDBAction {
    implicit rs =>
      eventService.eventDetails(id) match {
        case None => BadRequest(s"Failed to retrieve details for event $id")
        case Some(details) => Ok(views.html.reservations.create(details))
      }
  }
}
