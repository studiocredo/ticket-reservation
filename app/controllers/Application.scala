package controllers

import play.api._
import play.api.mvc._
import be.studiocredo.ShowService

import com.google.inject.Inject
import be.studiocredo.auth.{Secure, Authorization, AuthenticatorService}

class Application @Inject()(showService: ShowService, val authService: AuthenticatorService) extends Controller with Secure {
  val defaultAuthorization = None

  def index = AuthAwareDBAction { implicit request =>
    Ok(views.html.index(showService.nextShows(3)))
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
