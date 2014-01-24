package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids
import models.entities._
import com.google.inject.Inject
import models.entities.ShowPrereservation
import models.entities.ReservationQuotum

class PreReservationService @Inject()(orderService: OrderService) {
  import models.queries._
  import models.schema.tables._

  val SPRQ = Query(ShowPrereservations)
  val RQQ = Query(ReservationQuota)


  def preReservationsByShow(id: ids.ShowId)(implicit s: Session): List[ShowPrereservation] = {
    SPRQ.where(_.showId === id).list
  }

  def preReservationsByUser(id: ids.UserId)(implicit s: Session): List[ShowPrereservation] = {
    SPRQ.where(_.userId === id).list
  }

  def preReservationsByShowAndUser(showId: ids.ShowId, userId: ids.UserId)(implicit s: Session): List[ShowPrereservation] = {
    {
      for {
        spr <- ShowPrereservations
        if spr.showId === showId
        if spr.userId === userId
      } yield spr
    }.list
  }
  
  def quotaByUser(id: ids.UserId)(implicit s: Session): List[ReservationQuotum] = {
    RQQ.where(_.userId === id).list
  }


  //TODO: validate should be >= 0
  def unusedQuotaByUser(id: ids.UserId)(implicit s: Session): Int = {
    val query = for {
        (spr, show) <- ShowPrereservations leftJoin Shows on (_.showId === _.id)
        if spr.userId === id
      } yield (show.eventId, spr.quantity)
    val preres = query.list

    val quota = quotaByUser(id)
    quota.map{ rq: ReservationQuotum => rq.quota - preres.collect{case (eventId, quantity) if eventId == rq.eventId => quantity}.sum }.sum
  }

  //TODO: validate should be >= 0
  def pendingPrereservationsByUser(id: ids.UserId)(implicit s: Session): Int = {
    val preres = preReservationsByUser(id)
    val orders = orderService.byUserId(id)
    preres.map{ pr: ShowPrereservation => pr.quantity - orders.collect{case order if order.showId == pr.showId => 1}.sum }.sum
  }

  //TODO: validate prereservation should not exceed quotum
  def insert(showPrereservation: ShowPrereservation)(implicit s: Session) = ShowPrereservations.*.insert(showPrereservation)
  def insert(reservationQuotum: ReservationQuotum)(implicit s: Session) = ReservationQuota.*.insert(reservationQuotum)

}
