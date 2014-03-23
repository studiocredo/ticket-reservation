package controllers.admin

import be.studiocredo._
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

import scala.Some

class Prereservations @Inject()(preReservationService: PreReservationService, showService: ShowService, eventService: EventService, orderService: OrderService, val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
  val ListPage = Redirect(routes.Prereservations.list())

  def list(page: Int) = AuthDBAction { implicit rs =>
    Ok(views.html.admin.prereservations(preReservationService.page(page, 10, 1), userContext))
  }
}
