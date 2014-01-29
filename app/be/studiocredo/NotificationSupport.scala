package be.studiocredo

import models.ids.UserId
import play.api.templates.Html
import play.api.mvc.Controller
import be.studiocredo.util.DBSupport._

trait NotificationSupport extends Controller {
  val notificationService: NotificationService

  def getNotifications(id: UserId)(implicit request: be.studiocredo.auth.SecureRequest[_]): Html = {
    DB.withSession { implicit session =>
      views.html.notifications(notificationService.get(id))
    }
  }

  def notifications(implicit request: be.studiocredo.auth.SecureRequest[_]) : Html = {
    request.currentUser match { case Some(identity) => getNotifications(identity.id); case None => Html("") }
  }
}
