package controllers

import play.api._
import play.api.mvc._
import be.studiocredo.ShowService

import com.google.inject.Inject
import be.studiocredo.auth.{Secure, Authorization, AuthenticatorService}

class Application @Inject()(showService:ShowService, val authService: AuthenticatorService) extends Controller with Secure {
  val defaultAuthorization = None

  def index = AuthAwareDBAction { implicit request =>
    Ok(views.html.index(showService.nextShows(3)))
  }


  def javascriptRoutes = Action { implicit request =>
    import admin.routes.javascript._
     Ok(
      Routes.javascriptRouter("jsRoutes")(
        GroupDetails.ajaxMembers, MemberDetails.ajaxGroups
      )).as("text/javascript")
  }

}
