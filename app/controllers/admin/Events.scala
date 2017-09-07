package controllers.admin

import play.api.data.{Form, Forms}
import play.api.data.Forms._
import models.ids._
import models.entities._
import be.studiocredo.{EventService, NotificationService, UserContextSupport, UserService}
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService
import be.studiocredo.util.Money
import be.studiocredo.util.ServiceReturnValues._
import models.entities.EventEdit
import play.api.Play.current
import views.helper.Options
import views.helper.Options._

import scala.Some

class Events @Inject()(eventService: EventService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  val getPriceCategories: Seq[PriceCategory] = {
    current.configuration.getList("pricing").map{ config =>
      config.unwrapped().toArray.toSeq.map(c => PriceCategory(c.toString))
    }.getOrElse(Nil)
  }

  val priceCategoryOptions = Options.apply(getPriceCategories, PriceCategoryRenderer)

  val ListPage = Redirect(routes.Events.list())

  val eventForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "preReservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "preReservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "template" -> optional(text),
      "archived" -> boolean,
      "pricing" -> optional(Forms.list(mapping(
        "category" -> text,
        "price" -> of[Money]
        )(EventPriceEdit.apply)(EventPriceEdit.unapply)
      ))
    )(EventWithPriceEdit.apply)(EventWithPriceEdit.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val list = eventService.pageAll(page)
    Ok(views.html.admin.events(list, userContext))
  }


  def create() = AuthDBAction { implicit request =>
    Ok(views.html.admin.eventsCreateForm(eventForm, priceCategoryOptions, userContext))
  }
  def save() = AuthDBAction { implicit rs =>
    val bindedForm = eventForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => BadRequest(views.html.admin.eventsCreateForm(formWithErrors, priceCategoryOptions, userContext)),
      event => {
        eventService.insert(event.event).fold(
          error => BadRequest(views.html.admin.eventsCreateForm(bindedForm.withGlobalError(serviceMessage(error)), priceCategoryOptions, userContext)),
          success => ListPage.flashing("success" -> "Evenement '%s' aangemaakt".format(event.name))
        )
      }
    )
  }

  def edit(id: EventId) = AuthDBAction { implicit rs =>
    eventService.getEditWithPrice(id) match {
      case None => ListPage
      case Some(event) => Ok(views.html.admin.eventsEditForm(id, eventForm.fillAndValidate(event), priceCategoryOptions, userContext))
    }
  }
  def update(id: EventId) = AuthDBAction { implicit rs =>
    val bindedForm = eventForm.bindFromRequest
    bindedForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.eventsEditForm(id, formWithErrors, priceCategoryOptions, userContext)),
      event => {
        eventService.update(id, event.event).fold(
          error => BadRequest(views.html.admin.eventsEditForm(id, bindedForm.withGlobalError(serviceMessage(error)), priceCategoryOptions, userContext)),
          success => ListPage.flashing("success" -> "Evenement '%s' aangepast".format(event.name))
        )
      }
    )
  }
  def delete(id: EventId) = AuthDBAction { implicit rs =>
    eventService.delete(id)

    ListPage.flashing("success" -> "Evenement verwijderd")
  }
}
