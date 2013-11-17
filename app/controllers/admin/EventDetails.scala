package controllers.admin

import be.studiocredo._
import models.ids._
import play.api._
import org.joda.time.format.DateTimeFormat
import play.api.data.Form
import play.api.data.Forms._
import views.helper.Options
import models.entities.ShowEdit
import com.google.inject.Inject
import be.studiocredo.auth.{AuthenticatorService, SecuredDBRequest}

import models.admin._

class EventDetails @Inject()(eventService: EventService, showService: ShowService, venueService: VenueService, val authService: AuthenticatorService) extends AdminController {
  val logger = Logger("group-details")

  implicit val dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  val showForm = Form(
    mapping(
      "venue" -> of[VenueId],
      "date" -> jodaDate("yyyy-MM-dd HH:mm")
    )(NewShow.apply)(NewShow.unapply)
  )

  def view(id: EventId) = AuthDBAction { implicit rs =>
    page(id)
  }

  def addShow(id: EventId) = AuthDBAction { implicit rs =>
    showForm.bindFromRequest.fold(
      formWithErrors => page(id, formWithErrors, BadRequest),
      newShow => {
        showService.insert(ShowEdit(id, newShow.venueId, newShow.date, archived = false))

        Redirect(routes.EventDetails.view(id)).flashing("success" -> "Show added")
      }
    )
  }

  def page(id: EventId, form: Form[NewShow] = showForm, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for event $id")
      case Some(details) => status(views.html.admin.event(details, views.html.admin.eventAddshow(id, form, Options.apply(venueService.list(), Options.VenueRenderer))))
    }

  }

}


