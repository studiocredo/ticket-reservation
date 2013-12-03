package controllers.admin

import play.api.db.slick.Config.driver.simple._
import com.google.inject.Inject
import be.studiocredo.VenueService
import be.studiocredo.auth.AuthenticatorService
import models.ids.VenueId
import play.api.libs.json.{JsError, Json}
import models.entities._
import play.api.mvc.Result

class Floorplans @Inject()(venueService: VenueService, val authService: AuthenticatorService) extends AdminController {

  import FloorPlanJson._
  val defaultPlan = FloorPlan((1 to 10).map (_ => Row((1 to 20).map ( _=> Seat(SeatType.Normal)).toList, 0)).toList)

  def view(id: VenueId) = AuthDBAction { implicit rs =>
    forVenue(id) { venue =>
      Ok(views.html.admin.venueFloorPlan(venue))
    }
  }

  def ajaxFloorPlan(id: VenueId) = AuthDBAction { implicit rs =>
    forVenue(id) { venue => Ok(Json.toJson(venue.floorplan.getOrElse(defaultPlan))) }
  }

  def ajaxSaveFloorPlan(id: VenueId) = AuthDBAction(parse.json) { implicit rs =>
    rs.body.validate[FloorPlan].map {
      plan => {
        venueService.update(id, plan)
        Ok("")
      }
    }.recoverTotal {
      e => BadRequest("Detected error:" + JsError.toFlatJson(e))
    }
  }

  def forVenue(id: VenueId)(found: Venue => Result)(implicit s: Session): Result = {
    venueService.get(id) match {
      case Some(venue) => found(venue)
      case None => BadRequest(s"Venue $id not found")
    }
  }
}