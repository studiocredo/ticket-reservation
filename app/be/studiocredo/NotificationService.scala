package be.studiocredo

import com.google.inject.Inject
import models.ids.UserId
import models.entities.{NotificationType, NotificationEntry, Notification}
import be.studiocredo.util.DBSupport._
import models.HumanDateTime
import be.studiocredo.util.Joda._

class NotificationService @Inject()(prs: PreReservationService, os: OrderService, es: EventService, ss: ShowService) {
  def get(id: UserId)(implicit s: DBSession): List[Notification] = {
    get(List(id))
  }

  def get(ids: List[UserId])(implicit s: DBSession): List[Notification] = {
    val pr = prs.pendingPrereservationsByUsers(ids)
    val prEntries = pr.showMap.toSeq.sortBy(_._1.date).map {
      case(k,v) =>
        NotificationEntry(s"${k.name} ${HumanDateTime.formatDateTimeCompact(k.date)} ($v)", NotificationType.PendingPrereservation)
    }.toList

    val uq = prs.unusedQuotaByUsers(ids)
    val uqEntries = uq.eventMap.toSeq.sortBy(_._1.name.toLowerCase).map{case(k,v) => NotificationEntry(s"${k.name} ($v)", NotificationType.UnusedQuota)}.toList

    List(
      pr.total match {
        case 1 => Some(Notification("1 ongebruikte reservatie", prEntries))
        case p: Int if p > 1 => Some(Notification(s"$p ongebruikte reservaties", prEntries))
        case _ => None
      },
      uq.total match {
        case 1 => Some(Notification("1 ongebruikte pre-reservatie", uqEntries))
        case q: Int if q > 1 => Some(Notification(s"$q ongebruikte pre-preservaties", uqEntries))
        case _ => None
      }
    ).flatten
  }
}
