package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids._
import com.google.inject.Inject
import models.entities._
import models.entities.ShowPrereservation
import models.entities.ReservationQuotum
import scala.collection.mutable

class PreReservationService @Inject()(orderService: OrderService) {
  import models.schema.tables._

  val SPRQ = Query(ShowPrereservations)
  val RQQ = Query(ReservationQuota)


  def preReservationsByShow(id: ShowId)(implicit s: Session): List[ShowPrereservation] = {
    SPRQ.where(_.showId === id).list
  }

  def preReservationsByUser(id: UserId)(implicit s: Session): List[ShowPrereservation] = {
    SPRQ.where(_.userId === id).list
  }

  def preReservationsByUsers(ids: List[UserId])(implicit s: Session): List[ShowPrereservation] = {
    SPRQ.where(_.userId inSet ids).list
  }

  def preReservationsByShowAndUser(showId: ShowId, userId: UserId)(implicit s: Session): List[ShowPrereservation] = {
    {
      for {
        spr <- ShowPrereservations
        if spr.showId === showId
        if spr.userId === userId
      } yield spr
    }.list
  }
  
  def quotaByUser(id: UserId)(implicit s: Session): List[ReservationQuotum] = {
    RQQ.where(_.userId === id).list
  }

  def quotaByUsers(ids: List[UserId])(implicit s: Session): List[ReservationQuotum] = {
    RQQ.where(_.userId inSet ids).list
  }


  //TODO: validate should be >= 0
  //TODO: only for events that are not archived and not passed
  //TODO: only when pre-reservation period is active
  def unusedQuotaByUser(id: UserId)(implicit s: Session): UnusedQuotaDisplay = {
    unusedQuotaByUsers(List(id))
  }

  def unusedQuotaByUsers(ids: List[UserId])(implicit s: Session): UnusedQuotaDisplay = {
    val query = for {
      (spr, show) <- ShowPrereservations leftJoin Shows on (_.showId === _.id)
      if spr.userId inSet ids
    } yield (show.eventId, spr.quantity)
    val eventMap = mutable.Map[EventId, Int]().withDefaultValue(0)

    quotaByUsers(ids).foreach { q : ReservationQuotum => eventMap(q.eventId) += q.quota }
    query.list.foreach { t => eventMap(t._1) -= t._2}

    UnusedQuotaDisplay(eventMap.toMap)
  }

  //TODO: validate should be >= 0
  //TODO: only for events that are not archived and not passed
  //TODO: only when reservatin period is active
  def pendingPrereservationsByUser(id: UserId)(implicit s: Session): PendingPrereservationDisplay = {
    pendingPrereservationsByUsers(List(id))
  }

  def pendingPrereservationsByUsers(ids: List[UserId])(implicit s: Session): PendingPrereservationDisplay = {
    val showMap = mutable.Map[ShowId, Int]().withDefaultValue(0)

    preReservationsByUsers(ids).foreach { pr: ShowPrereservation => showMap(pr.showId) += pr.quantity }
    orderService.byUserIds(ids).foreach { o: TicketSeatOrder => showMap(o.showId) -= 1 }

    PendingPrereservationDisplay(showMap.toMap)
  }

  //TODO: validate prereservation should not exceed quotum
  def insert(showPrereservation: ShowPrereservation)(implicit s: Session) = ShowPrereservations.*.insert(showPrereservation)
  def insert(reservationQuotum: ReservationQuotum)(implicit s: Session) = ReservationQuota.*.insert(reservationQuotum)

}
