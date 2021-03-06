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
import models.entities._


class UserDetails @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService, orderService: OrderService, prereservationService: PreReservationService) extends AdminController with UserContextSupport {
  val logger = Logger("group-details")

  def view(id: UserId, showAll: Boolean) = AuthDBAction { implicit rs =>
    userService.find(id) match {
      case None => BadRequest(s"Gebruiker $id niet gevonden")
      case Some(details) => Ok(views.html.admin.user(getReservationHistory(details, showAll), userContext))
    }
  }

  def getReservationHistory(user: RichUser, showAll: Boolean)(implicit s: DBSession): RichUserWithReservationHistory = {
    val otherUsers = userService.findOtherUsers(user.user)
    val allUserIds = user.id :: otherUsers.map{_.id}
    RichUserWithReservationHistory(user, otherUsers, orderService.detailedOrdersByUsers(allUserIds, if (showAll) None else Some(activeOrderFilter)), prereservationService.preReservationsByUsers(allUserIds, if (showAll) None else Some(activePreReservationFilter)), prereservationService.pendingPrereservationsByUsers(allUserIds, if (showAll) None else Some(activePreReservationFilter)), prereservationService.quotaByUsers(allUserIds, if (showAll) None else Some(activeQuotaFilter)), prereservationService.unusedQuotaByUsers(allUserIds, if (showAll) None else Some(activeQuotaFilter)))
  }

  private def activeOrderFilter(t: (TicketOrder, TicketSeatOrder, Show, Event, Venue)): Boolean = {
    !t._3.archived && !t._4.archived
  }

  private def activePreReservationFilter(t: (ShowPrereservation, Show, Event, User, Venue)): Boolean = {
    !t._2.archived && !t._3.archived
  }

  private def activeQuotaFilter(t: (ReservationQuotum, Event, User)): Boolean = {
    !t._2.archived
  }
}
