package be.studiocredo

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
          case Some(identity) => Some(UserContext(notificationService.get(identity.otherUsers.map { _.id } :+ identity.id), identity.otherUsers))
          case None => None
        }
    }
  }
}
