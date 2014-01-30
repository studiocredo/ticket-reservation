package controllers.admin

import be.studiocredo._
import models.ids._
import play.api._
import org.joda.time.format.DateTimeFormat
import play.api.data.Form
import play.api.data.Forms._
import views.helper.Options
import com.google.inject.Inject
import be.studiocredo.auth.{AuthenticatorService, SecuredDBRequest}

import models.admin._

class EventDetails @Inject()(eventService: EventService, showService: ShowService, venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {
  val logger = Logger("group-details")

  implicit val dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  val showForm = Form(
    mapping(
      "venue" -> of[VenueId],
      "date" -> jodaDate("yyyy-MM-dd HH:mm")
    )(ShowEdit.apply)(ShowEdit.unapply)
  )

  def view(id: EventId) = AuthDBAction { implicit rs =>
    page(id)
  }

  def addShow(id: EventId) = AuthDBAction { implicit rs =>
    showForm.bindFromRequest.fold(
      formWithErrors => page(id, formWithErrors, BadRequest),
      newShow => {
        showService.insert(id, ShowEdit(newShow.venueId, newShow.date))

        Redirect(routes.EventDetails.view(id)).flashing("success" -> "Voorstelling toegevoegd")
      }
    )
  }

  def page(id: EventId, form: Form[ShowEdit] = showForm, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(details) => status(views.html.admin.event(details, views.html.admin.eventAddshow(id, form, Options.apply(venueService.list(), Options.VenueRenderer)), userContext))
    }
  }

  def editShow(id: EventId, showId: ShowId) = AuthDBAction { implicit rs =>
    showService.get(showId) match {
      case None => BadRequest(s"Voorstelling $id niet gevonden")
      case Some(show) => {
        Ok(views.html.admin.show(id, showId, showForm.fill(ShowEdit(show.venueId, show.date)), Options.apply(venueService.list(), Options.VenueRenderer), userContext))
      }
    }
  }

  def updateShow(id: EventId, showId: ShowId) = AuthDBAction { implicit rs =>
    showForm.bindFromRequest.fold(
      formWithErrors => page(id, formWithErrors, BadRequest),
      show => {
        showService.update(showId, show)

        Redirect(routes.EventDetails.view(id)).flashing("success" -> "Voorstelling aangepast")
      }
    )
  }

  def deleteShow(id: EventId, showId: ShowId) = AuthDBAction { implicit rs =>
    showService.delete(showId)
    Redirect(routes.EventDetails.view(id)).flashing("success" -> "Voorstelling verwijderd")
  }

}


