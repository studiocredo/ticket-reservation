package controllers

import play.api.mvc._
import play.api.db.slick._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities._
import be.studiocredo.VenueService

object Venues extends Controller {
  val venueService = new VenueService()

  val ListPage = Redirect(routes.Venues.list())

  val venueForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "archived" -> boolean
    )(VenueEdit.apply)(VenueEdit.unapply)
  )

  def list(page: Int) = DBAction { implicit rs =>
    val list = venueService.page(page)
    Ok(views.html.venues(list))
  }


  def create() = Action { implicit request =>
    Ok(views.html.venuesCreateForm(venueForm))
  }
  def save() = DBAction { implicit rs =>
    venueForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.venuesCreateForm(formWithErrors)),
      venue => {
        venueService.insert(venue)

        ListPage.flashing("success" -> "Venue '%s' has been created".format(venue.name))
      }
    )
  }

  def edit(id: VenueId) = DBAction { implicit rs =>
    venueService.getEdit(id) match {
      case None => ListPage
      case Some(venue) => Ok(views.html.venuesEditForm(id, venueForm.fillAndValidate(venue)))
    }
  }
  def update(id: VenueId) = DBAction { implicit rs =>
    venueForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.venuesEditForm(id, formWithErrors)),
      venue => {
        venueService.update(id, venue)

        ListPage.flashing("success" -> "Venue '%s' has been updated".format(venue.name))
      }
    )
  }
  def delete(id: VenueId) = DBAction { implicit rs =>
    venueService.delete(id)

    ListPage.flashing("success" -> "Venue has been deleted")
  }
}
