package controllers.admin

import be.studiocredo._
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities.FloorPlanJson._
import com.google.inject.Inject
import be.studiocredo.auth.{SecureRequest, AuthenticatorService}

import scala.Some
import controllers.auth.Mailer
import play.api.libs.json.Json
import models.entities.{TicketDistribution, SeatType}
import be.studiocredo.reservations.{TicketGenerator, ReservationEngineMonitorService, FloorProtocol}
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{SimpleResult, ResponseHeader}
import controllers.{routes => defaultRoutes}

case class OrderSearchFormData(search: String)

class Orders @Inject()(ticketService: TicketService, preReservationService: PreReservationService, showService: ShowService, eventService: EventService, orderService: OrderService, venueService: VenueService, orderEngine: ReservationEngineMonitorService, val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
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
            ListPage.flashing("error" -> "Bevestiging email verzonden")
          }
        }
      }
    }
  }

  def cancel(id: OrderId) = AuthDBAction { implicit rs =>
    import FloorProtocol._

    orderService.destroy(id) match {
      case 0 => BadRequest(s"Bestelling $id niet gevonden")
      case _ => {
        (orderEngine.floors) ! ReloadState
        ListPage.flashing(
          "success" -> s"Bestelling $id geannulleerd")
      }
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
