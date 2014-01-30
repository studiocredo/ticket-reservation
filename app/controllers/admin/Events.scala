package controllers.admin

import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities._
import be.studiocredo.{UserService, NotificationService, UserContextSupport, EventService}
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

class Events @Inject()(eventService: EventService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  val ListPage = Redirect(routes.Events.list())

  val eventForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "preReservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "preReservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "archived" -> boolean
    )(EventEdit.apply)(EventEdit.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val list = eventService.page(page)
    Ok(views.html.admin.events(list, userContext))
  }


  def create() = AuthDBAction { implicit request =>
    Ok(views.html.admin.eventsCreateForm(eventForm, userContext))
  }
  def save() = AuthDBAction { implicit rs =>
    eventForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsCreateForm(formWithErrors, userContext)),
      event => {
        eventService.insert(event)

        ListPage.flashing("success" -> "Evenement '%s' aangemaakt".format(event.name))
      }
    )
  }

  def edit(id: EventId) = AuthDBAction { implicit rs =>
    eventService.getEdit(id) match {
      case None => ListPage
      case Some(event) => Ok(views.html.admin.eventsEditForm(id, eventForm.fillAndValidate(event), userContext))
    }
  }
  def update(id: EventId) = AuthDBAction { implicit rs =>
    eventForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsEditForm(id, formWithErrors, userContext)),
      event => {
        eventService.update(id, event)

        ListPage.flashing("success" -> "Evenement '%s' aangepast".format(event.name))
      }
    )
  }
  def delete(id: EventId) = AuthDBAction { implicit rs =>
    eventService.delete(id)

    ListPage.flashing("success" -> "Evenement verwijderd")
  }
}
