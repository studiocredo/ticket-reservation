package controllers

import play.api._
import play.api.mvc._
import be.studiocredo.{UserService, UserContextSupport, NotificationService, ShowService}

import com.google.inject.Inject
import be.studiocredo.auth.{Secure, AuthenticatorService}

class Application @Inject()(showService: ShowService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {
  val defaultAuthorization = None

  def index = AuthAwareDBAction { implicit request =>
    Ok(views.html.index(showService.nextShows(3), userContext))
  }


  def javascriptRoutes = Action { implicit request =>
    import admin.routes.{javascript => ar}
    import routes.{javascript => mr}
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        mr.Events.ajaxFloorplan,

        ar.Floorplans.ajaxSaveFloorPlan, ar.Floorplans.ajaxFloorPlan
      )).as("text/javascript")
  }

}
