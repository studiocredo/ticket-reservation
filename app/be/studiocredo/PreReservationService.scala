package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids._
import com.google.inject.Inject
import models.entities.ShowPrereservation
import models.entities.ReservationQuotum

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
  def unusedQuotaByUser(id: UserId)(implicit s: Session): Int = {
    val query = for {
        (spr, show) <- ShowPrereservations leftJoin Shows on (_.showId === _.id)
        if spr.userId === id
      } yield (show.eventId, spr.quantity)
    val preres = query.list

    val quota = quotaByUser(id)
    quota.map{ rq: ReservationQuotum => rq.quota - preres.collect{case (eventId, quantity) if eventId == rq.eventId => quantity}.sum }.sum
  }

  def unusedQuotaByUsers(ids: List[UserId])(implicit s: Session): Int = {
    val query = for {
      (spr, show) <- ShowPrereservations leftJoin Shows on (_.showId === _.id)
      if spr.userId inSet ids
    } yield (show.eventId, spr.quantity)
    val preres = query.list

    val quota = quotaByUsers(ids)
    quota.map{ rq: ReservationQuotum => rq.quota - preres.collect{case (eventId, quantity) if eventId == rq.eventId => quantity}.sum }.sum
  }

  //TODO: validate should be >= 0
  def pendingPrereservationsByUser(id: UserId)(implicit s: Session): Int = {
    val preres = preReservationsByUser(id)
    val orders = orderService.byUserId(id)
    preres.map{ pr: ShowPrereservation => pr.quantity - orders.collect{case order if order.showId == pr.showId => 1}.sum }.sum
  }

  def pendingPrereservationsByUsers(ids: List[UserId])(implicit s: Session): Int = {
    val preres = preReservationsByUsers(ids)
    val orders = orderService.byUserIds(ids)
    preres.map{ pr: ShowPrereservation => pr.quantity - orders.collect{case order if order.showId == pr.showId => 1}.sum }.sum
  }

  //TODO: validate prereservation should not exceed quotum
  def insert(showPrereservation: ShowPrereservation)(implicit s: Session) = ShowPrereservations.*.insert(showPrereservation)
  def insert(reservationQuotum: ReservationQuotum)(implicit s: Session) = ReservationQuota.*.insert(reservationQuotum)

}
