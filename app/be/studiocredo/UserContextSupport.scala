package be.studiocredo

import be.studiocredo.auth.Roles
import play.api.mvc.Controller
import be.studiocredo.util.DBSupport._
import models.entities.UserContext

trait UserContextSupport extends Controller {
  val notificationService: NotificationService
  val userService: UserService

  def userContext(implicit request: be.studiocredo.auth.SecureRequest[_]): Option[UserContext] = {
    DB.withSession {
      implicit session =>
        request.currentUser match {
          case Some(identity) => Some(UserContext(notificationService.get(identity.allUsers), identity.otherUsers, identity.roles.contains(Roles.Admin)))
          case None => None
        }
    }
  }
}
