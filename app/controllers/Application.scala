package controllers

import play.api._
import play.api.mvc._
import be.studiocredo._

import com.google.inject.Inject
import be.studiocredo.auth.{Secure, AuthenticatorService}

class Application @Inject()(showService: ShowService, eventService: EventService, assetService: AssetService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport with BrowserDetectionSupport {
  val defaultAuthorization = None

  def index = AuthAwareDBAction { implicit request =>
    if (isOldExplorer) {
      Ok(views.html.reject())
    } else {
      val nextShows = Play.current.configuration.getInt("application.next-shows").getOrElse(4)
      Ok(views.html.index(showService.nextShows(nextShows), eventService.listUpcoming, assetService.listAvailable(request.user.map{_.id}), userContext))
    }
  }


  def javascriptRoutes = Action { implicit request =>
    import admin.routes.{javascript => ar}
    import routes.{javascript => mr}
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        mr.Events.ajaxFloorplan,
        mr.Events.ajaxAvailabilityFloorplan,
        mr.Orders.ajaxFloorplan,
        mr.Orders.ajaxMove,
        mr.Orders.ajaxMoveBest,
        ar.Orders.ajaxFloorplan,
        ar.Floorplans.ajaxSaveFloorPlan,
        ar.Floorplans.ajaxFloorPlan
      )).as("text/javascript")
  }

}
