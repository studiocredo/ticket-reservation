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
    findPreReservation(p).exists.run
  }

  def activePreReservationsByUsers(ids: List[UserId])(implicit s: Session): List[ShowPrereservationDetail] = {
    preReservationsByUsers(ids, Some(activePreReservationFilter))
  }

  private def activePreReservationFilter(t: (ShowPrereservation, Show, Event, User, Venue)): Boolean = {
    t._3.reservationAllowed
  }

  def preReservationsByUsersAndEvent(ids: List[UserId], event: EventId)(implicit s: Session): List[ShowPrereservationDetail] = {
    preReservationsByUsers(ids, Some(eventPreReservationFilter(event)))
  }

  private def eventPreReservationFilter(event: EventId)(t: (ShowPrereservation, Show, Event, User, Venue)): Boolean = {
    t._3.id == event
  }

  def preReservationsByUsers(ids: List[UserId], f: Option[((ShowPrereservation, Show, Event, User, Venue)) => Boolean] = None)(implicit s: Session): List[ShowPrereservationDetail] = {
    val query = for {
      ((((showPreres, show), event), user), venue) <- ShowPrereservations.leftJoin(Shows).on(_.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id).leftJoin(Users).on(_._1._1.userId === _.id).leftJoin(Venues).on(_._1._1._2.venueId === _.id)
      if showPreres.userId inSet ids
      if !show.archived
      if !event.archived
    } yield (showPreres, show, event, user, venue)
    val list = f match {
      case Some(f) => query.list.withFilter(f)
      case None => query.list
    }
    list.map{ case (spr: ShowPrereservation, s: Show, e: Event, u: User, v: Venue) => ShowPrereservationDetail(EventShow(s.id, e.id, e.name, s.venueId, v.name, s.date, s.archived), u, spr.quantity)}
  }

  def totalQuotaByUsersAndEvent(users: List[UserId], event: EventId)(implicit s: Session): Option[Int] = {
    val query = for {
      rq <- ReservationQuota
      if rq.userId inSet users
      if rq.eventId === event
    } yield (rq.quota)
    query.sum.run
  }

  def activeQuotaByUsers(ids: List[UserId], f: Option[((ReservationQuotum, Event, User)) => Boolean] = None)(implicit s: Session): List[ReservationQuotumDetail] = {
    quotaByUsers(ids, Some(activeQuotaFilter))
  }

  private def activeQuotaFilter(t: (ReservationQuotum, Event, User)): Boolean = {
    t._2.preReservationAllowed
  }

  def quotaByUsers(ids: List[UserId], f: Option[((ReservationQuotum, Event, User)) => Boolean] = None)(implicit s: Session): List[ReservationQuotumDetail] = {
    val query = for {
      ((rq, e), u) <- ReservationQuota.leftJoin(Events).on(_.eventId === _.id).leftJoin(Users).on(_._1.userId === _.id)
      if rq.userId inSet ids
    } yield (rq, e, u)
    val list = f match {
      case Some(f) => query.list.withFilter(f)
      case None => query.list
    }
    list.map{ case (rq, e, u) => ReservationQuotumDetail(e, u, rq.quota)}
  }


  //TODO: validate should be >= 0
  //TODO: only for events that are not archived and not passed
  //TODO: only when pre-reservation period is active
  def activeUnusedQuotaByUsers(ids: List[UserId])(implicit s: Session): UnusedQuotaDisplay = {
    unusedQuotaByUsers(ids, activeQuotaByUsers(ids))
  }

  def unusedQuotaByUsers(ids: List[UserId])(implicit s: Session): UnusedQuotaDisplay = {
    unusedQuotaByUsers(ids, quotaByUsers(ids))
  }

  private def unusedQuotaByUsers(ids: List[UserId], rqd: List[ReservationQuotumDetail])(implicit s: Session): UnusedQuotaDisplay = {
    val query = for {
      ((spr, show), event) <- ShowPrereservations.leftJoin(Shows).on(_.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id)
      if spr.userId inSet ids
      if !show.archived
      if !event.archived
    } yield (event, spr.quantity)
    val eventMap = mutable.Map[Event, Int]().withDefaultValue(0)

    rqd.foreach { q : ReservationQuotumDetail => eventMap(q.event) += q.quota }
    val x = query.list
      x.foreach { t => eventMap(t._1) -= t._2}

    UnusedQuotaDisplay(eventMap.filter(_._2 > 0).toMap)
  }

  //TODO: validate should be >= 0
  def activePendingPrereservationsByUsers(ids: List[UserId])(implicit s: Session): PendingPrereservationDisplay = {
    pendingPrereservationsByUsers(ids, activePreReservationsByUsers(ids))
  }

  def pendingPrereservationsByUsers(ids: List[UserId])(implicit s: Session): PendingPrereservationDisplay = {
    pendingPrereservationsByUsers(ids, preReservationsByUsers(ids))
  }

  def pendingPrereservationsByUsersAndEvent(ids: List[UserId], event: EventId)(implicit s: Session): Map[ShowId, Int] = {
    val showMap = mutable.Map[ShowId, Int]().withDefaultValue(0)
    val prd = preReservationsByUsersAndEvent(ids, event)

    prd.foreach { pr: ShowPrereservationDetail => showMap(pr.show.id) = showMap(pr.show.id) + pr.quantity }
    orderService.seatsByUsers(ids).foreach { o: TicketSeatOrderDetail => if (showMap(o.show.id) > 0) showMap(o.show.id) -= 1 }

    showMap.toMap.withDefaultValue(0)
  }

  private def pendingPrereservationsByUsers(ids: List[UserId], prd: List[ShowPrereservationDetail])(implicit s: Session): PendingPrereservationDisplay = {
    val showMap = mutable.Map[EventShow, Int]().withDefaultValue(0)

    prd.foreach { pr: ShowPrereservationDetail => showMap(pr.show) = showMap(pr.show) + pr.quantity }
    orderService.seatsByUsers(ids).foreach { o: TicketSeatOrderDetail => if (showMap(o.show) > 0) showMap(o.show) -= 1 }

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
    val userMap = mutable.Map(users.map{userId => (userId, totalQuotaByUsersAndEvent(List(userId), event))}.toSeq: _*)
    showPrereservations.foreach { showPrereservationUpdate =>
      var quantity = showPrereservationUpdate.quantity
      users.foreach { user =>
        val quantityByUser = Math.min(quantity, userMap(user).getOrElse(0))
        quantity -= quantityByUser
        if (userMap.contains(user)) {
          userMap.put(user, Some(userMap(user).get - quantityByUser))
        }
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
    val eventShowMap = Shows.leftJoin(Events).on(_.eventId === _.id).where(_._1.id inSet showMap.keys).list.map{ case (s: Show, e: Event) => (s.id, EventShow(s.id, s.eventId, e.name, s.venueId, "dummy", s.date, s.archived))}.toMap
    showMap.keys.foreach { showId: ShowId => showMap(showId) -= orderService.capacity(eventShowMap(showId), users).byType(SeatType.Normal) }

    showMap.view.filter{ case (showId: ShowId, overCapacity: Int) => overCapacity > 0 }.map{case (showId: ShowId, overCapacity: Int) => (eventShowMap(showId), overCapacity)}.headOption match {
      case Some((show, overCapacity)) => Left(serviceFailure("prereservations.capacity.exceeded", List(overCapacity, show.name, HumanDateTime.formatDateTimeCompact(show.date))))
      case None => Right(serviceSuccess("prereservations.capacity.success"))
    }
  }

  private def findPreReservation(p: ShowPrereservation)= SPRQ.where(_.showId === p.showId).where(_.userId === p.userId)
}
