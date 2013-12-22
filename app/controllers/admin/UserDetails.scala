package controllers.admin

import be.studiocredo.UserService
import models.ids._
import play.api._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService


class UserDetails @Inject()(userService: UserService, val authService: AuthenticatorService) extends AdminController {
  val logger = Logger("group-details")


  def view(id: UserId) = AuthDBAction { implicit rs =>
    userService.find(id) match {
      case None => BadRequest(s"Failed to retrieve details for user $id")
      case Some(details) => Ok(views.html.admin.user(details))
    }
  }
}
