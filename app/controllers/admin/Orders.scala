package controllers.admin

import be.studiocredo._
import be.studiocredo.util.DBSupport._
import play.api.data.validation.{ValidationResult, Invalid, Valid, Constraint}
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import models.ids._
import models.entities.FloorPlanJson._
import com.google.inject.Inject
import be.studiocredo.auth.{SecureRequest, AuthenticatorService}

import scala.Some
import controllers.auth.Mailer
import play.api.libs.json.Json
import models.entities._
import be.studiocredo.reservations.{TicketGenerator, ReservationEngineMonitorService, FloorProtocol}
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{SimpleResult, ResponseHeader}
import controllers.{routes => defaultRoutes}
import be.studiocredo.util.ServiceReturnValues._
import be.studiocredo.util.Money
import models.entities.OrderDetailEdit
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import views.helper.OrderPaidOption

case class OrderSearchFormData(search: Option[String], paid: OrderPaidOption.Option)

class Orders @Inject()(ticketService: TicketService, preReservationService: PreReservationService, showService: ShowService, eventService: EventService, orderService: OrderService, venueService: VenueService, orderEngine: ReservationEngineMonitorService, val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
  val ListPage = Redirect(routes.Orders.list())

  val orderSearchForm = Form(
    mapping(
      "search" -> optional(nonEmptyText(3)),
      "paid" -> of[OrderPaidOption.Option]
    )(OrderSearchFormData.apply)(OrderSearchFormData.unapply)
  )

  val validUserId: Constraint[UserId] = Constraint({
    userId => DB.withSession {
      implicit session => userService.find(userId).fold(Invalid("Ongeldige gebruiker id"): ValidationResult)(ru => Valid)
    }
  })

  val orderForm = Form(
    mapping(
      "userId" -> of[UserId].verifying(validUserId),
      "billingName" -> nonEmptyText,
      "billingAddress" -> text,
      "comments" -> optional(text),
      "seats" -> Forms.list[TicketSeatOrderEdit] (
        mapping(
          "ticketOrderId" -> of[TicketOrderId],
          "seat" -> of[SeatId],
          "price" -> of[Money]
        )(TicketSeatOrderEdit.apply)(TicketSeatOrderEdit.unapply)
      )
    )(OrderDetailEdit.apply)(OrderDetailEdit.unapply)
  )

  def list(search: Option[String], paid: String, showAll: Boolean, page: Int) = AuthDBAction { implicit rs =>
    val bindedForm = orderSearchForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => {
        val list = orderService.page(page, showAll)
        Ok(views.html.admin.orders(list, formWithErrors, showAll, userContext))
      },
      orderFormData => {
        val list = orderService.page(page, showAll, 10, 1, orderFormData.search, orderFormData.paid)
        Ok(views.html.admin.orders(list, bindedForm, showAll, userContext))
      }
    )
  }

  def details(order: OrderId) = AuthDBAction { implicit rs =>
    orderService.getWithPayments(order) match {
      case None => BadRequest(s"Bestelling $order niet gevonden")
      case Some(order) => {
        Ok(views.html.admin.order(order, userContext))
      }
    }
  }

  def show(show: ShowId) = AuthDBAction { implicit rs =>
    showService.get(show) match {
      case None => BadRequest(s"Voorstelling $show niet gevonden")
      case Some(showDetail) => {
        val details = eventService.eventDetails(showDetail.eventId).get
        val currentUserContext = userContext
        val venueShow = details.shows.flatMap(_.shows).find(_.id == show)
        val showAvailability = venueShow.map {
          s => preReservationService.detailedAvailability(showService.getEventShow(s.id))
        }
        Ok(views.html.admin.showOrders(details, showAvailability.get, currentUserContext))
      }
    }
  }

  def edit(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.getEdit(id) match {
      case None => ListPage
      case Some(order) => Ok(views.html.admin.orderEditForm(id, orderForm.fillAndValidate(order), userContext))
    }
  }

  def update(id: OrderId) = AuthDBAction { implicit rs =>
    val bindedForm = orderForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => BadRequest(views.html.admin.orderEditForm(id, formWithErrors, userContext)),
      order => {
        orderService.update(id, order).fold(
          error => BadRequest(views.html.admin.orderEditForm(id, bindedForm.withGlobalError(serviceMessage(error)), userContext)),
          success => Redirect(routes.Orders.details(id)).flashing("success" -> "Bestelling aangepast")
        )
      }
    )
  }

  private def getTicketUrl(ticket: TicketDistribution)(implicit request: SecureRequest[_]): String = {
    defaultRoutes.Orders.ticketDetails(ticket.reference).absoluteURL()
  }

  def sendAndCreateTicket(id: OrderId) = AuthDBAction { implicit rs =>
    ticketService.create(id).fold(
      error => ListPage.flashing("error" -> "Ticket aanmaken mislukt"),
      ticket => {
        ticketService.generate(ticket, getTicketUrl(ticket)).fold(
          error => ListPage.flashing("error" -> "Ticket aanmaken mislukt"),
          ticket => {
            userService.find(ticket.order.user.id) match {
              case None => BadRequest(s"Gebruiker ${ticket.order.user.id} niet gevonden")
              case Some(user) => {
                Mailer.sendTicketEmail(user, ticket)
                ListPage.flashing("error" -> "Ticket email verzonden")
              }
            }
          }
        )
      }
    )
  }


  def showTicket(id: OrderId) = AuthDBAction { implicit rs =>
    ticketService.findOrCreate(id).fold(
      error => ListPage.flashing("error" -> "Ticket aanmaken mislukt"),
      ticket => {
        ticketService.generate(ticket, getTicketUrl(ticket)).fold(
          error => ListPage.flashing("error" -> "Ticket aanmaken mislukt"),
          ticket => {
            SimpleResult(
              header = ResponseHeader(200),
              body = Enumerator(ticket.pdf)
            )
          }
        )
      }
    )
  }

  def sendAndCreateNewTickets() = AuthDBAction { implicit rs =>
    val newTickets = ticketService.createForNew()

    val (success, failure) = newTickets.map { ticket =>
      ticketService.generate(ticket, getTicketUrl(ticket)).fold(
        error => None,
        ticket => Some(ticket)
      )
    }.partition(_.isDefined)

    success.flatten.foreach { ticket =>
      userService.find(ticket.order.user.id) match {
        case None => ()
        case Some(user) => Mailer.sendTicketEmail(user, ticket)
      }
    }

    if (failure.isEmpty) {
      if (success.isEmpty) {
        ListPage.flashing("success" -> "Geen nieuwe tickets te verzenden")
      } else {
        ListPage.flashing("success" -> s"${success.length} ticket(s) verzonden")
      }
    } else {
      ListPage.flashing("error" -> s"${success.length} ticket(s) verzonden, ${failure.length} ticket(s) niet verzonden")
    }
  }

  def confirm(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order)  => {
        userService.find(order.order.userId) match {
          case None => BadRequest(s"Gebruiker ${order.order.userId} niet gevonden")
          case Some(user) => {
            Mailer.sendOrderConfirmationEmail(user, order)
            ListPage.flashing("success" -> "Bevestiging email verzonden")
          }
        }
      }
    }
  }

  def cancel(id: OrderId) = AuthDBAction { implicit rs =>
    import FloorProtocol._

    ticketService.find(id) match {
      case Nil =>
        orderService.destroy(id) match {
          case 0 => BadRequest(s"Bestelling $id niet gevonden")
          case _ => {
            (orderEngine.floors) ! ReloadFullState()
            ListPage.flashing(
              "success" -> s"Bestelling $id geannuleerd")
          }
        }
      case _ => ListPage.flashing("error" -> "Ticket(s) reeds gedistribueerd. Deze bestelling kan niet geannuleerd worden")
    }
  }

  def ajaxFloorplan(id: ShowId) = AuthAwareDBAction { implicit rs =>
    val plan = for {
      show <- showService.get(id)
      venue <- venueService.get(show.venueId)
      fp <- venue.floorplan
    } yield (fp)

    plan match {
      case None => BadRequest(s"Zaalplan voor show $id niet gevonden")
      case Some(plan) => {
        Ok(Json.toJson(venueService.fillFloorplanDetailed(plan, orderService.detailsByShowId(id), Nil, SeatType.values.toList)))
      }
    }
  }

}
