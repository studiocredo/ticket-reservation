package controllers

import play.api.db.slick._
import be.studiocredo._
import play.api.Play.current
import models.ids._
import play.api._
import play.api.mvc._
import org.joda.time.format.DateTimeFormat
import play.api.data.Form
import play.api.data.Forms._
import models.NewShow
import views.helper.Options
import models.entities.ShowEdit


object EventDetails extends Controller {
  val logger = Logger("group-details")
  val eventService = new EventService()
  val showService = new ShowService()
  val venueService = new VenueService()

  implicit val dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  val showForm = Form(
    mapping(
      "venue" -> of[VenueId],
      "date" -> jodaDate("yyyy-MM-dd HH:mm")
    )(NewShow.apply)(NewShow.unapply)
  )

  def view(id: EventId) = DBAction { implicit rs =>
    page(id)
  }

  def addShow(id: EventId) = DBAction { implicit rs =>
    showForm.bindFromRequest.fold(
      formWithErrors => page(id, formWithErrors, BadRequest),
      newShow => {
        showService.insert(ShowEdit(id, newShow.venueId, newShow.date, archived = false))

        Redirect(routes.EventDetails.view(id)).flashing("success" -> "Show added")
      }
    )
  }

  def page(id: EventId, form: Form[NewShow] = showForm, status: Status = Ok)(implicit rs: DBSessionRequest[_]) = {
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for event $id")
      case Some(details) => status(views.html.event(details, views.html.eventAddshow(id, form, Options.apply(venueService.list(), Options.VenueRenderer))))
    }

  }

}


