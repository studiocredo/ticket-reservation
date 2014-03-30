package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth._
import play.api.mvc.Controller
import models.ids.{OrderId, ShowId, EventId}
import models.entities.FloorPlanJson._
import play.api.data.Form
import play.api.data.Forms._
import controllers.auth.Mailer
import scala.Some
import be.studiocredo.auth.SecuredDBRequest
import play.api.libs.json.Json
import models.entities.OrderEdit

case class ShowReservationForm(showId: ShowId, quantity: Int)

class Orders @Inject()(eventService: EventService, orderService: OrderService, showService: ShowService, preReservationService: PreReservationService, venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {

  val defaultAuthorization = Some(Authorization.ANY)

  def reservationForm(showId: ShowId): Form[ShowReservationForm] = Form(
    mapping(
      "show" -> ignored(showId),
      "quantity" -> number(min = 0)
    )(ShowReservationForm.apply)(ShowReservationForm.unapply)
  )

  def start(id: EventId) = AuthDBAction { implicit rs =>
    val users = rs.currentUser match {
      case None => Nil
      case Some(identity) => identity.id :: identity.otherUsers.map { _.id }
    }
    eventService.eventReservationDetails(id, users) match {
      case Some(event) if event.event.reservationAllowed => {
        //assume that there is max only one unprocessed order per usergroup
        //if no order is found, create a new one
        val order = orderService.unprocessedOrdersByUsers(users).headOption.getOrElse(orderService.create(rs.currentUser.get.user))
        Redirect(controllers.routes.Orders.view(order, id))
      }
      case _ => BadRequest(s"Evenement $id niet gevonden of reservaties niet toegelaten")
    }
  }

  def view(order: OrderId, event: EventId) = AuthDBAction { implicit rs =>
    viewPage(order, event)
  }

  def confirm(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order)  => {
        val currentUser = rs.currentUser.get
        //TODO: set ordered processed -> true in orderservice
        Mailer.sendOrderConfirmationEmail(currentUser.user, order)
        Redirect(routes.Orders.overview(id))
      }
    }
  }

  def overview(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order)  => {
        Ok(views.html.orderOverview(order, userContext))
      }
    }
  }
  
  def cancel(id: OrderId) = AuthDBAction { implicit rs =>
    ???
  }
  
  def viewShow(id: ShowId) = AuthDBAction { implicit rs =>
    showService.get(id) match {
      case None => BadRequest(s"Voorstelling $id niet gevonden")
      case Some(showDetail) => {
        val details = eventService.eventDetails(showDetail.eventId).get
        val currentUserContext = userContext
        val venueShow = details.shows.flatMap(_.shows).find(_.id == id)
        val showAvailability = venueShow.map {
          s => preReservationService.availability(showService.getEventShow(s.id))
        }
        Ok(views.html.reservationFloorplan(details, showAvailability.get, currentUserContext))
      }
    }
  }


 def cancelShow(id: ShowId) = AuthDBAction { implicit rs =>
    ???
  }

  def saveShow(id: ShowId) = AuthDBAction { implicit rs =>
    ???
//    val bindedForm = reservationForm.bindFromRequest
//    bindedForm.fold(
//      formWithErrors => page(id, Some(formWithErrors), BadRequest),
//      res => {
//        val currentUser = rs.currentUser.get
//        val userIds = currentUser.id :: userContext.get.otherUsers.map{_.id}
//        validateReservations(bindedForm, res, reservationService.totalQuotaByUsersAndEvent(userIds, id)).fold(
//          formWithErrors => page(id, Some(formWithErrors), BadRequest),
//          success => {
//            reservationService.updateOrInsert(id, preres.showPrereservations.map{ spr => ShowPrereservationUpdate(spr.showId, spr.quantity) }, userIds ).fold(
//              failure => page(id, Some(bindedForm.withGlobalError(serviceMessage(failure))), BadRequest),
//              success => {
//                Mailer.sendPrereservationSavedEmail(currentUser.user, eventService.eventPrereservationDetails(id, userIds))
//                Redirect(routes.Application.index).flashing("success" -> serviceMessage(success))
//              }
//            )
//          }
//        )
//      }
//    )
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
        Ok(Json.toJson(venueService.fillFloorplanReservations(plan, orderService.byShowId(id), Nil)))
      }
    }
  }

  private def viewPage(id: OrderId, event: EventId, form: Option[Map[ShowId, ShowReservationForm]] = None, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.id :: identity.otherUsers.map { _.id }
    }
    eventService.eventReservationDetails(event, users) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(event) => {
        orderService.get(id) match {
          case None => BadRequest(s"Bestelling $id niet gevonden")
          case Some(order) if !order.order.processed => {
            form match {
              case Some(forms) => status(views.html.order(event, order, forms.map{ case (show, srf) => (show, reservationForm(show).fill(srf)) }.toMap, userContext))
              case _ => {
                val showReservations = event.shows.map {
                  _.shows.map {
                    _.id
                  }
                }.flatten.map {
                  id => (id, reservationForm(id).fill(ShowReservationForm(id, event.pendingPrereservationsByShow(id))))
                }.toMap
                status(views.html.order(event, order, showReservations, userContext))
              }
            }
          }
          case _ => BadRequest(s"Bestelling $id is afgesloten")
        }
      }
    }
  }
}
