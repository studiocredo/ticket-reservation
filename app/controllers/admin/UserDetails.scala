package controllers.admin

import be.studiocredo._
import models.ids._
import play.api._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService
import be.studiocredo.util.DBSupport._
import models.admin.RichUser
import scala.Some
import models.admin.RichUserWithReservationHistory
import models.entities.User


class UserDetails @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService, orderService: OrderService, prereservationService: PreReservationService) extends AdminController with UserContextSupport {
  val logger = Logger("group-details")

  def view(id: UserId) = AuthDBAction { implicit rs =>
    userService.find(id) match {
      case None => BadRequest(s"Gebruiker $id niet gevonden")
      case Some(details) => Ok(views.html.admin.user(getReservationHistory(details), userContext))
    }
  }

  def getReservationHistory(user: RichUser)(implicit s: DBSession): RichUserWithReservationHistory = {
    val otherUsers = userService.findOtherUsers(user.user)
    val allUserIds = user.id :: otherUsers.map{_.id}
    RichUserWithReservationHistory(user, otherUsers, orderService.detailedOrdersByUsers(allUserIds), prereservationService.preReservationsByUsers(allUserIds), prereservationService.pendingPrereservationsByUsers(allUserIds), prereservationService.quotaByUsers(allUserIds), prereservationService.unusedQuotaByUsers(allUserIds))
  }
}
