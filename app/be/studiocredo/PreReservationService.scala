package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids._
import com.google.inject.Inject
import models.entities._
import scala.collection.mutable

class PreReservationService @Inject()(orderService: OrderService) {
  import models.schema.tables._

  val SPRQ = Query(ShowPrereservations)
  val RQQ = Query(ReservationQuota)

  def preReservationsByUser(id: UserId)(implicit s: Session): List[ShowPrereservationDetail] = {
    preReservationsByUsers(List(id))
  }

  def preReservationsByUsers(ids: List[UserId])(implicit s: Session): List[ShowPrereservationDetail] = {
    val query = for {
      (((showPreres, show), event), user) <- ShowPrereservations.leftJoin(Shows).on(_.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id).leftJoin(Users).on(_._1._1.userId === _.id)
      if showPreres.userId inSet ids
    } yield (showPreres, show, event, user)
    query.list.map{ case (spr: ShowPrereservation, s: Show, e: Event, u: User) => ShowPrereservationDetail(EventShow(s.id, e.id, e.name, s.venueId, s.date, s.archived), u, spr.quantity)}
  }

  def quotaByUser(id: UserId)(implicit s: Session): List[ReservationQuotumDetail] = {
    quotaByUsers(List(id))
  }

  def quotaByUsers(ids: List[UserId])(implicit s: Session): List[ReservationQuotumDetail] = {
    val query = for {
      ((rq, e), u) <- ReservationQuota.leftJoin(Events).on(_.eventId === _.id).leftJoin(Users).on(_._1.userId === _.id)
      if rq.userId inSet ids
    } yield (rq, e, u)
    query.list.map{ case (rq, e, u) => ReservationQuotumDetail(e, u, rq.quota)}
  }


  //TODO: validate should be >= 0
  //TODO: only for events that are not archived and not passed
  //TODO: only when pre-reservation period is active
  def unusedQuotaByUser(id: UserId)(implicit s: Session): UnusedQuotaDisplay = {
    unusedQuotaByUsers(List(id))
  }

  def unusedQuotaByUsers(ids: List[UserId])(implicit s: Session): UnusedQuotaDisplay = {
    val query = for {
      ((spr, show), event) <- ShowPrereservations.leftJoin(Shows).on(_.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id)
      if spr.userId inSet ids
    } yield (event, spr.quantity)
    val eventMap = mutable.Map[Event, Int]().withDefaultValue(0)

    quotaByUsers(ids).foreach { q : ReservationQuotumDetail => eventMap(q.event) += q.quota }
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
    val showMap = mutable.Map[EventShow, Int]().withDefaultValue(0)

    preReservationsByUsers(ids).foreach { pr: ShowPrereservationDetail => showMap(pr.show) += pr.quantity }
    orderService.seatsByUsers(ids).foreach { o: TicketSeatOrderDetail => showMap(o.show) -= 1 }

    PendingPrereservationDisplay(showMap.toMap)
  }

  //TODO: validate prereservation should not exceed quotum
  def insert(showPrereservation: ShowPrereservation)(implicit s: Session) = ShowPrereservations.*.insert(showPrereservation)
  def insert(reservationQuotum: ReservationQuotum)(implicit s: Session) = ReservationQuota.*.insert(reservationQuotum)

}
