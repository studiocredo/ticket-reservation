package controllers.admin

import play.api.mvc._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities._
import be.studiocredo.VenueService
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

class Venues @Inject()(venueService: VenueService, val authService: AuthenticatorService) extends AdminController {

  val ListPage = Redirect(routes.Venues.list())

  val venueForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "archived" -> boolean
    )(VenueEdit.apply)(VenueEdit.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val list = venueService.page(page)
    Ok(views.html.admin.venues(list))
  }


  def create() = AuthAction { implicit request =>
    Ok(views.html.admin.venuesCreateForm(venueForm))
  }
  def save() = AuthDBAction { implicit rs =>
    venueForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.venuesCreateForm(formWithErrors)),
      venue => {
        venueService.insert(venue)

        ListPage.flashing("success" -> "Venue '%s' has been created".format(venue.name))
      }
    )
  }

  def edit(id: VenueId) = AuthDBAction { implicit rs =>
    venueService.getEdit(id) match {
      case None => ListPage
      case Some(venue) => Ok(views.html.admin.venuesEditForm(id, venueForm.fillAndValidate(venue)))
    }
  }
  def update(id: VenueId) = AuthDBAction { implicit rs =>
    venueForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.venuesEditForm(id, formWithErrors)),
      venue => {
        venueService.update(id, venue)

        ListPage.flashing("success" -> "Venue '%s' has been updated".format(venue.name))
      }
    )
  }
  def delete(id: VenueId) = AuthDBAction { implicit rs =>
    venueService.delete(id)

    ListPage.flashing("success" -> "Venue has been deleted")
  }
}