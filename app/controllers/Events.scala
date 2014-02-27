package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.{Authorization, Secure, AuthenticatorService}
import play.api.mvc.Controller
import models.ids.{UserId, VenueId, ShowId, EventId}
import models.entities.{ShowAvailability, EventShow, UserContext, FloorPlanJson}
import play.api.libs.json.Json
import scala.Some
import be.studiocredo.util.DBSupport._

class Events @Inject()(venueService: VenueService, eventService: EventService, showService: ShowService, preReservationService: PreReservationService, orderService: OrderService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService : UserService) extends Controller with Secure with UserContextSupport {

  val defaultAuthorization = Some(Authorization.ANY)

  def list(page: Int) = AuthAwareDBAction { implicit rs =>
    Ok(views.html.events(eventService.page(page), userContext))
  }

  def view(id: EventId) = AuthAwareDBAction { implicit rs =>
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(details) => {
        val currentUserContext = userContext
        Ok(views.html.event(details, None, hasQuota(id, currentUserContext), currentUserContext))
      }
    }
  }

  def viewShow(eventId: EventId, showId: ShowId) = AuthAwareDBAction { implicit rs =>
    eventService.eventDetails(eventId) match {
      case None => BadRequest(s"Evenement $eventId niet gevonden")
      case Some(details) => {
        val currentUserContext = userContext
        val show = details.shows.flatMap(_.shows).find(_.id == showId)
        val showAvailability = show.map{ s => preReservationService.capacity(showService.getEventShow(s.id))}
        Ok(views.html.event(details, showAvailability, hasQuota(eventId, currentUserContext), currentUserContext))
      }
    }
  }

  private def hasQuota(eventId: EventId, userContext: Option[UserContext])(implicit request: be.studiocredo.auth.SecureRequest[_]): Boolean = {
    request.currentUser match {
      case Some(user) => {
        val userIds = user.id :: userContext.get.otherUsers.map { _.id }
        DB.withSession {
          implicit session =>
            preReservationService.totalQuotaByUsersAndEvent(userIds, eventId) match {
              case Some(total) => total > 0
              case None => false
            }
        }
      }
      case None => false
    }
  }

  import FloorPlanJson._

  def ajaxFloorplan(id: VenueId) = AuthAwareDBAction { implicit rs =>
    val plan = for {
      venue <- venueService.get(id)
      fp <- venue.floorplan
    } yield Ok(Json.toJson(fp))

    plan.getOrElse(BadRequest(s"Zaalplan voor locatie $id niet gevonden"))
  }

  def ajaxAvailabilityFloorplan(id: ShowId) = AuthAwareDBAction { implicit rs =>
    val plan = for {
      show <- showService.get(id)
      venue <- venueService.get(show.venueId)
      fp <- venue.floorplan
    } yield (fp)

    plan match {
      case None => BadRequest(s"Zaalplan voor show $id niet gevonden")
      case Some(plan) => {
        val users = rs.user match {
          case None => Nil
          case Some(identity) => identity.id :: identity.otherUsers.map{_.id}
        }
        Ok(Json.toJson(venueService.fillFloorplan(plan, orderService.byShowId(id), users)))
      }
    }
  }
}
