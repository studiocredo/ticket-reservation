package controllers.auth

import play.api.mvc._
import be.studiocredo.auth.{AuthenticatorService, Secure}
import com.google.inject.Inject
import be.studiocredo.{NotificationSupport, NotificationService}

class Auth @Inject()(val authService: AuthenticatorService, val notificationService: NotificationService) extends Controller with Secure with NotificationSupport {
  val defaultAuthorization = None

  def notAuthorized() = AuthAwareDBAction { implicit request =>
    Forbidden(views.html.auth.notAuthorized(notifications))
  }

}
