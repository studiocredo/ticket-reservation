package controllers.admin

import be.studiocredo.{UserContextSupport, NotificationService, UserService}
import models.ids._
import play.api._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService


class UserDetails @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
  val logger = Logger("group-details")


  def view(id: UserId) = AuthDBAction { implicit rs =>
    userService.find(id) match {
      case None => BadRequest(s"Gebruiker $id niet gevonden")
      case Some(details) => Ok(views.html.admin.user(details, userContext))
    }
  }
}
