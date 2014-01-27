package be.studiocredo

import models.ids.UserId
import play.api.templates.Html
import play.api.mvc.Controller
import be.studiocredo.util.DBImplicits

trait NotificationSupport extends Controller with DBImplicits {
  val notificationService: NotificationService

  def getNotifications(id: UserId)(implicit request: be.studiocredo.auth.SecureAwareDBRequest[_]): Html = {
    views.html.notifications(notificationService.get(id))
  }

  def notifications(implicit request: be.studiocredo.auth.SecureAwareDBRequest[_]) : Html = {
    request.currentUser match { case Some(identity) => getNotifications(identity.id); case None => Html("") }
  }

  def notifications2(implicit request: be.studiocredo.auth.SecuredDBRequest[_]) : Html = {
    getNotifications2(request.currentUser.get.id)
  }

  def getNotifications2(id: UserId)(implicit request: be.studiocredo.auth.SecuredDBRequest[_]): Html = {
    views.html.notifications(notificationService.get(id))
  }
}
