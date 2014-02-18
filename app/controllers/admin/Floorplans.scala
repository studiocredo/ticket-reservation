package controllers.admin

import play.api.db.slick.Config.driver.simple._
import com.google.inject.Inject
import be.studiocredo.{UserService, NotificationService, UserContextSupport, VenueService}
import be.studiocredo.auth.AuthenticatorService
import models.ids.VenueId
import play.api.libs.json.{JsError, Json}
import models.entities._
import play.api.mvc.Result
import be.studiocredo.util.ServiceReturnValues._
import models.entities.Row
import models.entities.Venue
import models.entities.FloorPlan
import models.entities.SeatId
import scala.Some
import models.entities.Seat

class Floorplans @Inject()(venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  import FloorPlanJson._
  val defaultPlan = FloorPlan((1 to 10).map (row => Row((1 to 20).map ( seat => Seat(SeatId(s"$row-$seat"), SeatType.Normal, 1)).toList, 0)).toList)

  def view(id: VenueId) = AuthDBAction { implicit rs =>
    forVenue(id) { venue =>
      Ok(views.html.admin.venueFloorPlan(venue, userContext))
    }
  }

  def ajaxFloorPlan(id: VenueId) = AuthDBAction { implicit rs =>
    forVenue(id) { venue => Ok(Json.toJson(venue.floorplan.getOrElse(defaultPlan))) }
  }

  def ajaxSaveFloorPlan(id: VenueId) = AuthDBAction(parse.json) { implicit rs =>
    rs.body.validate[FloorPlan].map {
      plan => {
        venueService.update(id, plan).fold(
          error => BadRequest(serviceMessage(error)),
          success => Ok("")
        )
      }
    }.recoverTotal {
      e => BadRequest("Er heeft zich een fout voorgedaan:" + JsError.toFlatJson(e))
    }
  }

  def forVenue(id: VenueId)(found: Venue => Result)(implicit s: Session): Result = {
    venueService.get(id) match {
      case Some(venue) => found(venue)
      case None => BadRequest(s"Locatie $id niet gevonden")
    }
  }
}
