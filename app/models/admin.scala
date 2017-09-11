package models

import be.studiocredo.auth.Password
import be.studiocredo.util.Joda._
import be.studiocredo.util.Money
import models.entities.EventQuotaConstraints.validations
import models.entities._
import models.ids._
import org.joda.time.DateTime
import play.api.data.validation.{Constraint, Invalid, Valid}

object admin {

  case class RichUser(user: User, detail: UserDetail) {
    val id: UserId = user.id
    val name: String = user.name
    val username: String = user.username
    val password: Password = user.password
    val email: Option[String] = detail.email
    val address: Option[String] = detail.address
    val phone: Option[String] = detail.phone
    val addressLines: List[String] = address.map(_.split("\n").toList).getOrElse(Nil)
    val active: Boolean = user.active
  }

  case class RichUserWithReservationHistory(user: RichUser, otherUsers: List[User], orders: List[OrderDetail], prereservations: List[ShowPrereservationDetail], pendingPrereservations: PendingPrereservationDisplay, reservationQuota: List[ReservationQuotumDetail], unusedQuota: UnusedQuotaDisplay) {
    val prereservationsByShow: Map[EventShow, List[ShowPrereservationDetail]] = prereservations.groupBy(_.show)
    val unusedPreReservationsByShow: Map[EventShow, Int] = pendingPrereservations.showMap
    val shows: List[EventShow] = (prereservations.map(_.show) ++ pendingPrereservations.showMap.keys).distinct.sortBy(_.date)
    val events: List[Event] = (reservationQuota.map(_.event) ++ unusedQuota.eventMap.keys).distinct
    val quotaByEvent: Map[Event, List[ReservationQuotumDetail]] = reservationQuota.groupBy(_.event)
    val unusedQuotaByEvent: Map[Event, Int] = unusedQuota.eventMap
  }

  case class UserFormData(name: String, username: String, email: Option[String], address: Option[String], phone: Option[String], active: Boolean)


  case class EventDetail(event: Event, shows: List[VenueShows], pricing: Option[EventPricing], assets: List[Asset]) {
    val id: EventId = event.id
    val name: String = event.name
    val reservationAllowed: Boolean = event.reservationAllowed
    val preReservationAllowed: Boolean = event.preReservationAllowed
    val orderedShows: List[Show] = shows.flatMap(_.shows).sortBy(_.date)
    val orderedVenueShows: List[(VenueShows, Show)] = shows.flatMap(vs => vs.shows.map((vs, _))).sortBy(_._2.date)
  }

  case class EventPrereservationsDetail(event: Event, users: List[User], shows: List[VenueShows], prereservations: List[ShowPrereservationDetail], reservationQuota: Option[Int]) {
    val id: EventId = event.id

    def prereservationsByShow(showId: ShowId): Int = {
      prereservations.collect { case sprd if sprd.show.id == showId => sprd.quantity }.sum
    }

    val orderedShows: List[(Venue, Show)] = shows.flatMap { vs => vs.shows.map((vs.venue, _)) }.sortBy(_._2.date)
  }

  case class EventReservationsDetail(event: EventDetail, users: List[User], shows: List[VenueShows], pendingPrereservationsByShow: Map[ShowId, Int]) {
    val id: EventId = event.id

    //WARNING the number of tickets per session should be at least equal
    //      to the total number of prereservations for that family otherwise
    //      they cannot order all their prereservations at once
    val totalQuota: Option[Int] = event.event.quota.map(_.quota(users.length))

    val orderedShows: List[(Venue, Show)] = shows.flatMap { vs => vs.shows.map((vs.venue, _)) }.sortBy(_._2.date)
  }

  case class ShowEdit(venueId: VenueId, date: DateTime, reservationStart: Option[DateTime], reservationEnd: Option[DateTime], archived: Boolean)

  object ShowConstraints {  //TODO
    def forEvent(event: Event): Seq[Constraint[ShowEdit]] = Seq(
      Constraint[ShowEdit]("constraint.show.date.start", Nil) { show =>
        show.reservationStart match {
          case Some(startDate) if event.reservationStart.exists(_.isAfter(startDate)) => Invalid("error.show.date.start", Nil)
          case Some(startDate) if event.reservationEnd.exists(_.isBefore(startDate)) => Invalid("error.show.date.start", Nil)
          case _ => Valid
        }
      },
      Constraint[ShowEdit]("constraint.show.date.end", Nil) { show =>
        show.reservationEnd match {
          case Some(endDate) if event.reservationStart.exists(_.isAfter(endDate)) => Invalid("error.show.date.end", Nil)
          case Some(endDate) if event.reservationEnd.exists(_.isBefore(endDate)) => Invalid("error.show.date.end", Nil)
          case _ => Valid
        }
      },
      Constraint[ShowEdit]("constraint.show.date.before", Nil) { show =>
        (show.reservationStart, show.reservationEnd) match {
          case (Some(startDate), Some(endDate)) if startDate.isAfter(endDate) => Invalid("error.show.date.before", Nil)
          case _ => Valid
        }
      }
    )
  }

  object RichAsset {
    def init(e: Event, a: Asset) = RichAsset(a.id, e, a.name, a.price, a.availableStart, a.availableEnd, a.downloadable, a.objectKey, a.archived)
  }
  case class RichAsset(id: AssetId, event: Event, name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean)

  case class AssetEdit(name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean)

  case class VenueShows(venue: Venue, shows: List[Show])

  case class VenueShow(venue: Venue, show: Show)

}
