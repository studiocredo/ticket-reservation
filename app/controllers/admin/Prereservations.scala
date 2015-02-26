package controllers.admin

import be.studiocredo._
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

import scala.Some

case class PrereservationsSearchFormData(search: Option[String])

class Prereservations @Inject()(preReservationService: PreReservationService, showService: ShowService, eventService: EventService, orderService: OrderService, val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
  val ListPage = Redirect(routes.Prereservations.list())

  val prereservationsSearchForm = Form(
    mapping(
      "search" -> optional(nonEmptyText(3))
    )(PrereservationsSearchFormData.apply)(PrereservationsSearchFormData.unapply)
  )
  
  def list(search: Option[String], showAll: Boolean, page: Int) = AuthDBAction { implicit rs =>
    val bindedForm = prereservationsSearchForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => {
        val list = preReservationService.page(page, showAll)
        Ok(views.html.admin.prereservations(list, formWithErrors, showAll, userContext))
      },
      orderFormData => {
        val list = preReservationService.page(page, showAll, 10, 1, orderFormData.search)
        Ok(views.html.admin.prereservations(list, bindedForm, showAll, userContext))
      }
    )
  }
}
