package controllers.admin

import be.studiocredo.auth.AuthenticatorService
import be.studiocredo.util.Money
import be.studiocredo.util.ServiceReturnValues._
import be.studiocredo.{EventService, NotificationService, UserContextSupport, UserService}
import com.google.inject.Inject
import models.entities._
import models.ids._
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.{Form, Forms}
import play.api.mvc.SimpleResult
import views.helper.Options
import views.helper.Options._

class Events @Inject()(eventService: EventService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  val getPriceCategories: Seq[PriceCategory] = {
    current.configuration.getList("pricing").map{ config =>
      config.unwrapped().toArray.toSeq.map(c => PriceCategory(s"pricing.$c"))
    }.getOrElse(Nil)
  }

  val priceCategoryOptions = Options.apply(getPriceCategories, PriceCategoryRenderer)

  val ListPage: SimpleResult = Redirect(routes.Events.list())

  val eventForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> text,
      "preReservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "preReservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "template" -> optional(text),
      "quota" -> optional(mapping(
        "default" -> number.verifying("format.numeric.positive", n => n > 0),
        "values" -> of[Map[Int, Int]](jsonMapFormatter).verifying(EventQuotaConstraints.validEventQuota)
        )(EventQuota.apply)(EventQuota.unapply)
      ),
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
        eventService.update(id, event).fold(
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
