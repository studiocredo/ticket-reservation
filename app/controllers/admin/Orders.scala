package controllers.admin

import be.studiocredo._
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

import scala.Some

case class OrderSearchFormData(search: String)

class Orders @Inject()(preReservationService: PreReservationService, showService: ShowService, eventService: EventService, orderService: OrderService, val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
  val ListPage = Redirect(routes.Orders.list())

  val orderSearchForm = Form(
    mapping(
      "search" -> nonEmptyText(3)
    )(OrderSearchFormData.apply)(OrderSearchFormData.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val bindedForm = orderSearchForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => {
        val list = orderService.page(page)
        Ok(views.html.admin.orders(list, formWithErrors, userContext))
      },
      orderFormData => {
        val list = orderService.page(page, 10, 1, Some(orderFormData.search))
        Ok(views.html.admin.orders(list, bindedForm, userContext))
      }
    )
  }

  def show(show: ShowId) = AuthDBAction { implicit rs =>
    showService.get(show) match {
      case None => BadRequest("Voorstelling $show niet gevonden")
      case Some(showDetail) => {
        val details = eventService.eventDetails(showDetail.eventId).get
        val currentUserContext = userContext
        val venueShow = details.shows.flatMap(_.shows).find(_.id == show)
        val showAvailability = venueShow.map {
          s => preReservationService.availability(showService.getEventShow(s.id))
        }
        Ok(views.html.admin.showOrders(details, showAvailability.get, currentUserContext))
      }
    }
  }
}
