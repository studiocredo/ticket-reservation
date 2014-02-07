package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.ids._
import com.google.inject.Inject
import models.entities._
import scala.collection.mutable
import models.{HumanDateTime, ids}
import be.studiocredo.util.ServiceReturnValues._

class PreReservationService @Inject()(orderService: OrderService) {
  import models.schema.tables._

  val SPRQ = Query(ShowPrereservations)
  val RQQ = Query(ReservationQuota)

  def hasPreReservation(p: ShowPrereservation)(implicit s: Session): Boolean = {
    findPreReservation(p).length.run > 0
  }

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

  def totalQuotaByUsersAndEvent(users: List[UserId], event: EventId)(implicit s: Session): Option[Int] = {
    val query = for {
      rq <- ReservationQuota
      if rq.userId inSet users
      if rq.eventId === event
    } yield (rq.quota)
    query.sum.run
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

    UnusedQuotaDisplay(eventMap.filter(_._2 > 0).toMap)
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

    PendingPrereservationDisplay(showMap.filter(_._2 > 0).toMap)
  }

  //TODO: validate prereservation should not exceed user quotum for show and total should not exceed show capacity
  def insert(showPrereservation: ShowPrereservation)(implicit s: Session) = ShowPrereservations.*.insert(showPrereservation)

  private def updateQuantity(showPrereservation: ShowPrereservation)(implicit s: Session) = {
    showPrereservation.quantity match {
      case 0 => if (hasPreReservation(showPrereservation)) delete(showPrereservation)
      case _ => if (hasPreReservation(showPrereservation)) findPreReservation(showPrereservation).map(_.quantity).update(showPrereservation.quantity) else ShowPrereservations.*.insert(showPrereservation)
    }
  }
  def delete(showPrereservation: ShowPrereservation)(implicit s: Session) = ShowPrereservations.where(_.showId === showPrereservation.showId).where(_.userId === showPrereservation.userId).delete

  def insert(reservationQuotum: ReservationQuotum)(implicit s: Session) = ReservationQuota.*.insert(reservationQuotum)


  //TODO redistribute all preres over all users (in order) making sure individual capacity is not overrun, set all others to 0
  //verify
  def updateOrInsert(event: EventId, showPrereservations: List[ShowPrereservationUpdate], users: List[UserId])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateQuota(event, showPrereservations, users).fold(
      error => Left(error),
      success => validateCapacity(event, showPrereservations, users).fold(
        error => Left(error),
        success => fillPrereservations(event, showPrereservations, users).fold(
          error => Left(error),
          success => Right(success)
        )
      )
    )
  }

  private def fillPrereservations(event: EventId, showPrereservations: List[ShowPrereservationUpdate], users: List[UserId])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    //TODO fill up
    showPrereservations.foreach { showPrereservationUpdate =>
      var quantity = showPrereservationUpdate.quantity
      users.foreach { user =>
        val quantityByUser = Math.min(quantity, totalQuotaByUsersAndEvent(List(user), EventId(1)).getOrElse(quantity))
        quantity -= quantityByUser
        updateQuantity(ShowPrereservation(showPrereservationUpdate.showId, user, quantityByUser))
      }
    }
    Right(serviceSuccess("prereservations.success"))
  }

  private def validateQuota(event: EventId, showPrereservations: List[ShowPrereservationUpdate], users: List[UserId])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    showPrereservations.map{_.quantity}.sum > totalQuotaByUsersAndEvent(users, event).getOrElse(0) match {
      case true => Left(serviceFailure("prereservations.quota.exceeded"))
      case _ => Right(serviceSuccess("prereservations.quota.success"))
    }
  }

  private def validateCapacity(event: EventId, showPrereservations: List[ShowPrereservationUpdate], users: List[UserId])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    //validate venue capacity
    val showMap = mutable.Map[ShowId, Int]().withDefaultValue(0)
    showPrereservations.foreach { pr: ShowPrereservationUpdate => showMap(pr.showId) += pr.quantity }
    val eventShowMap = Shows.leftJoin(Events).on(_.eventId === _.id).where(_._1.id inSet showMap.keys).list.map{ case (s: Show, e: Event) => (s.id, EventShow(s.id, s.eventId, e.name, s.venueId, s.date, s.archived))}.toMap
    showMap.keys.foreach { showId: ShowId => showMap(showId) -= orderService.capacity(eventShowMap(showId), users).byType(SeatType.Normal) }

    showMap.view.filter{ case (showId: ShowId, overCapacity: Int) => overCapacity > 0 }.map{case (showId: ShowId, overCapacity: Int) => (eventShowMap(showId), overCapacity)}.headOption match {
      case Some((show, overCapacity)) => Left(serviceFailure("prereservations.capacity.exceeded", List(overCapacity, show.name, HumanDateTime.formatDateTimeCompact(show.date))))
      case None => Right(serviceSuccess("prereservations.capacity.success"))
    }
  }

  private def findPreReservation(p: ShowPrereservation)= SPRQ.where(_.showId === p.showId).where(_.userId === p.userId)
}
