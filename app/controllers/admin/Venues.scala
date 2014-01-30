package controllers.admin

import play.api.mvc._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities._
import be.studiocredo.{UserService, UserContextSupport, NotificationService, VenueService}
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

class Venues @Inject()(venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

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
    Ok(views.html.admin.venues(list, userContext))
  }


  def create() = AuthDBAction { implicit request =>
    Ok(views.html.admin.venuesCreateForm(venueForm, userContext))
  }
  def save() = AuthDBAction { implicit rs =>
    venueForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.venuesCreateForm(formWithErrors, userContext)),
      venue => {
        venueService.insert(venue)

        ListPage.flashing("success" -> "Locatie '%s' aangemaakt".format(venue.name))
      }
    )
  }

  def edit(id: VenueId) = AuthDBAction { implicit rs =>
    venueService.getEdit(id) match {
      case None => ListPage
      case Some(venue) => Ok(views.html.admin.venuesEditForm(id, venueForm.fillAndValidate(venue), userContext))
    }
  }
  def update(id: VenueId) = AuthDBAction { implicit rs =>
    venueForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.venuesEditForm(id, formWithErrors, userContext)),
      venue => {
        venueService.update(id, venue)

        ListPage.flashing("success" -> "Locatie '%s' aangepast".format(venue.name))
      }
    )
  }
  def delete(id: VenueId) = AuthDBAction { implicit rs =>
    venueService.delete(id)

    ListPage.flashing("success" -> "Locatie verwijderd")
  }
}
