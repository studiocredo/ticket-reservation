package controllers.auth

import play.api.mvc._
import be.studiocredo.auth.{AuthenticatorService, Secure}
import com.google.inject.Inject
import be.studiocredo.{UserService, UserContextSupport, NotificationService}

class Auth @Inject()(val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {
  val defaultAuthorization = None

  def notAuthorized() = AuthAwareDBAction { implicit request =>
    Forbidden(views.html.auth.notAuthorized(userContext))
  }

}
