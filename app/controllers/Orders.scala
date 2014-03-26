package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth._
import play.api.mvc.Controller
import models.ids.{ShowId, EventId}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import be.studiocredo.auth.SecuredDBRequest

case class ShowReservationForm(showId: ShowId, quantity: Int)
case class ReservationForm(showReservations: List[ShowReservationForm])

class Orders @Inject()(eventService: EventService, orderService: OrderService, showService: ShowService, preReservationService: PreReservationService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {

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

  def start(id: EventId, show: Option[ShowId]) = AuthDBAction { implicit rs =>
    page(id)
  }

  def reservations(show: ShowId) = AuthDBAction { implicit rs =>
    showService.get(show) match {
      case None => BadRequest("Voorstelling $show niet gevonden")
      case Some(showDetail) => {
        val details = eventService.eventDetails(showDetail.eventId).get
        val currentUserContext = userContext
        val venueShow = details.shows.flatMap(_.shows).find(_.id == show)
        val showAvailability = venueShow.map {
          s => preReservationService.availability(showService.getEventShow(s.id))
        }
        Ok(views.html.reservationFloorplan(details, showAvailability.get, currentUserContext))
      }
    }
  }

  def save(id: EventId) = AuthDBAction { implicit rs =>
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

  private def page(eventId: EventId, form: Option[Form[ReservationForm]] = None, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.id :: identity.otherUsers.map { _.id }
    }
    eventService.eventReservationDetails(eventId, users) match {
      case None => BadRequest(s"Evenement $eventId niet gevonden")
      case Some(event) => {
        form match {
          case Some(form) => status(views.html.order(event, form , userContext))
          case _ => {
            val showReservations = event.shows.map{_.shows.map{_.id}}.flatten.map{id => ShowReservationForm(id, event.pendingPrereservationsByShow(id))}
            status(views.html.order(event, reservationForm.fill(ReservationForm(showReservations)), userContext))
          }
        }
      }
    }
  }
}
