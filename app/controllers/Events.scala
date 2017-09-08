package controllers

import be.studiocredo._
import be.studiocredo.auth.{AuthenticatorService, Authorization, Secure}
import be.studiocredo.util.DBSupport._
import com.google.inject.Inject
import models.entities.{UserContext, _}
import models.ids.{EventId, ShowId, VenueId}
import play.api.libs.json.Json
import play.api.mvc.Controller

class Events @Inject()(venueService: VenueService, eventService: EventService, showService: ShowService, preReservationService: PreReservationService, orderService: OrderService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService : UserService) extends Controller with Secure with UserContextSupport {

  val defaultAuthorization = Some(Authorization.ANY)

  def list(page: Int) = AuthAwareDBAction { implicit rs =>
    Ok(views.html.events(eventService.page(page), userContext))
  }

  def view(id: EventId) = AuthAwareDBAction { implicit rs =>
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(details) =>
        if (details.event.archived) {
          BadRequest(s"Evenement $id niet beschikbaar")
        } else {
          details.shows.headOption match {
            case None => BadRequest(s"Evenement $id heeft geen shows")
            case Some(venueShow) =>
              venueShow.shows.headOption match {
                case None => BadRequest(s"Evenement $id heeft geen shows")
                case Some(show) =>
                  if (show.archived) {
                    BadRequest(s"Show ${show.id} van evenement $id niet beschikbaar")
                  } else {
                    Redirect(routes.Events.viewShow(id, show.id))
                  }
              }
          }
        }
    }
  }

  def viewShow(eventId: EventId, showId: ShowId) = AuthAwareDBAction {
    implicit rs =>
      eventService.eventDetails(eventId) match {
        case None => BadRequest(s"Evenement $eventId niet gevonden")
        case Some(details) =>
          if (details.event.archived) {
            BadRequest(s"Evenement $eventId niet beschikbaar")
          } else {
            val currentUserContext = userContext
            details.shows.flatMap(_.shows).find(_.id == showId) match {
              case None => BadRequest(s"Evenement $eventId heeft geen shows")
              case Some(show) =>
                if (show.archived) {
                  BadRequest(s"Show $showId van evenement $eventId niet beschikbaar")
                } else {
                val showAvailability = preReservationService.availability(showService.getEventShow(show.id))
                Ok(views.html.event(details, showAvailability, hasQuota(eventId, currentUserContext), currentUserContext))
                }
            }
          }
      }
  }

  private def hasQuota(eventId: EventId, userContext: Option[UserContext])(implicit request: be.studiocredo.auth.SecureRequest[_]): Boolean = {
    request.currentUser match {
      case Some(identity) =>
        DB.withSession {
          implicit session =>
            preReservationService.totalQuotaByUsersAndEvent(identity.allUsers, eventId) match {
              case Some(total) => total > 0
              case None => false
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
    val maybePlan = for {
      show <- showService.get(id)
      venue <- venueService.get(show.venueId)
      fp <- venue.floorplan
    } yield fp

    maybePlan match {
      case None => BadRequest(s"Zaalplan voor show $id niet gevonden")
      case Some(plan) =>
        val users = rs.user match {
          case None => Nil
          case Some(identity) => identity.allUsers
        }
        Ok(Json.toJson(venueService.fillFloorplan(plan, orderService.detailsByShowId(id), users)))
    }
  }
}
