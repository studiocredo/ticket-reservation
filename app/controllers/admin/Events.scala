package controllers.admin

import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities._
import be.studiocredo.{NotificationService, NotificationSupport, EventService}
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

class Events @Inject()(eventService: EventService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with NotificationSupport {

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
    Ok(views.html.admin.events(list, notifications))
  }


  def create() = AuthDBAction { implicit request =>
    Ok(views.html.admin.eventsCreateForm(eventForm, notifications))
  }
  def save() = AuthDBAction { implicit rs =>
    eventForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsCreateForm(formWithErrors, notifications)),
      event => {
        eventService.insert(event)

        ListPage.flashing("success" -> "Event '%s' has been created".format(event.name))
      }
    )
  }

  def edit(id: EventId) = AuthDBAction { implicit rs =>
    eventService.getEdit(id) match {
      case None => ListPage
      case Some(event) => Ok(views.html.admin.eventsEditForm(id, eventForm.fillAndValidate(event), notifications))
    }
  }
  def update(id: EventId) = AuthDBAction { implicit rs =>
    eventForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsEditForm(id, formWithErrors, notifications)),
      event => {
        eventService.update(id, event)

        ListPage.flashing("success" -> "Event '%s' has been updated".format(event.name))
      }
    )
  }
  def delete(id: EventId) = AuthDBAction { implicit rs =>
    eventService.delete(id)

    ListPage.flashing("success" -> "Event has been deleted")
  }
}
