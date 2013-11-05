package controllers.auth

import play.api.mvc._
import be.studiocredo.auth.{AuthenticatorService, Secure}
import com.google.inject.Inject

class Auth @Inject()(val authService: AuthenticatorService) extends Controller with Secure {
  val defaultAuthorization = None

  def notAuthorized() = AuthAwareAction { implicit request =>
    Forbidden(views.html.auth.notAuthorized())
  }

}
