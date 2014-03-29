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

case class ShowReservationForm(showId: ShowId, quantity: Int)
case class ReservationForm(showReservations: List[ShowReservationForm])

class Orders @Inject()(eventService: EventService, orderService: OrderService, showService: ShowService, preReservationService: PreReservationService, venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {

  val defaultAuthorization = Some(Authorization.ANY)

  val reservationForm: Form[ReservationForm] = Form(
    mapping(
      "res" -> list[ShowReservationForm] (
        mapping(
          "show" -> of[ShowId],
          "quantity" -> number(min = 0)
        )(ShowReservationForm.apply)(ShowReservationForm.unapply)
      )
    )(ReservationForm.apply)(ReservationForm.unapply)
  )

  def start(id: EventId) = AuthDBAction { implicit rs =>
    ???
    //TODO lookup open order (take copuled users into account) or start new order & redirect to view page
  }

  def view(id: OrderId) = AuthDBAction { implicit rs =>
    viewPage(id)
  }

  def confirm(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order)  => {
        val currentUser = rs.currentUser.get
        //TODO: set ordered processed -> true in orderservice
        Mailer.sendOrderConfirmationEmail(currentUser.user, order)
        Redirect(routes.Application.index).flashing("success" -> "Bedankt voor je bestelling. Je ontvangt binnenkort een e-mail met de betalingsgegevens.")
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

  private def viewPage(id: OrderId, form: Option[Form[ReservationForm]] = None, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.id :: identity.otherUsers.map { _.id }
    }
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order) => {
        form match {
          case Some(form) => ??? //status(views.html.order(order, form , userContext))
          case _ => {
            ???
            //val showReservations = event.shows.map{_.shows.map{_.id}}.flatten.map{id => ShowReservationForm(id, event.pendingPrereservationsByShow(id))}
            //status(views.html.order(event, reservationForm.fill(ReservationForm(showReservations)), userContext))
          }
        }
      }
    }
  }
}
