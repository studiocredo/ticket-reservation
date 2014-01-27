package be.studiocredo

import com.google.inject.Inject
import models.ids.UserId
import models.entities.Notification
import be.studiocredo.util.DBSupport._

class NotificationService @Inject()(prs: PreReservationService, os: OrderService) {
  def get(id: UserId)(implicit s: DBSession): List[Notification] = {
    List(
      prs.pendingPrereservationsByUser(id) match {
        case 1 => Some(Notification("Je heb nog 1 reservatie vast te leggen"))
        case p: Int if p > 1 => Some(Notification(s"Je hebt nog $p reservaties vast te leggen"))
        case _ => None
      },
      prs.unusedQuotaByUser(id) match {
        case 1 => Some(Notification("Je hebt nog recht op 1 pre-reservatie"))
        case q: Int if q > 0 => Some(Notification(s"Je hebt nog recht op $q pre-preservaties"))
        case _ => None
      }
    ).flatten
  }
}
