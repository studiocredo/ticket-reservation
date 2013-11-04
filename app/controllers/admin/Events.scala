package controllers.admin

import play.api.mvc._
import play.api.db.slick._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities._
import be.studiocredo.EventService

object Events extends Controller {
  val eventService = new EventService()

  val ListPage = Redirect(routes.Events.list())

  val eventForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "archived" -> boolean
    )(EventEdit.apply)(EventEdit.unapply)
  )

  def list(page: Int) = DBAction { implicit rs =>
    val list = eventService.page(page)
    Ok(views.html.admin.events(list))
  }


  def create() = Action { implicit request =>
    Ok(views.html.admin.eventsCreateForm(eventForm))
  }
  def save() = DBAction { implicit rs =>
    eventForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsCreateForm(formWithErrors)),
      event => {
        eventService.insert(event)

        ListPage.flashing("success" -> "Event '%s' has been created".format(event.name))
      }
    )
  }

  def edit(id: EventId) = DBAction { implicit rs =>
    eventService.getEdit(id) match {
      case None => ListPage
      case Some(event) => Ok(views.html.admin.eventsEditForm(id, eventForm.fillAndValidate(event)))
    }
  }
  def update(id: EventId) = DBAction { implicit rs =>
    eventForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsEditForm(id, formWithErrors)),
      event => {
        eventService.update(id, event)

        ListPage.flashing("success" -> "Event '%s' has been updated".format(event.name))
      }
    )
  }
  def delete(id: EventId) = DBAction { implicit rs =>
    eventService.delete(id)

    ListPage.flashing("success" -> "Event has been deleted")
  }
}
