package models

import be.studiocredo.auth.Password
import be.studiocredo.util.Joda._
import be.studiocredo.util.Money
import models.entities._
import models.ids._
import models.schema.Archiveable
import org.joda.time.DateTime

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

  object EventReservationsDetail {
    val maxQuantityPerReservation = 10
  }

  case class EventReservationsDetail(event: EventDetail, users: List[User], shows: List[VenueShows], pendingPrereservationsByShow: Map[ShowId, Int]) {
    val id: EventId = event.id

    //TODO: WARNING the number of tickets per session should be at least equal
    //      to the total number of prereservations for that family otherwise
    //      they cannot order all their prereservations at once
    val totalQuota: Int = users.length match {
      case 0 => 0
      case 1 => 5
      case 2 => 8
      case _ => 10
    } //TODO: make number of tickets per session configurable

    val orderedShows: List[(Venue, Show)] = shows.flatMap { vs => vs.shows.map((vs.venue, _)) }.sortBy(_._2.date)
  }

  case class ShowEdit(venueId: VenueId, date: DateTime, archived: Boolean)

  object RichAsset {
    def init(e: Event, a: Asset) = RichAsset(a.id, e, a.name, a.price, a.availableStart, a.availableEnd, a.downloadable, a.objectKey, a.archived)
  }
  case class RichAsset(id: AssetId, event: Event, name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean)

  case class AssetEdit(name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean)

  case class VenueShows(venue: Venue, shows: List[Show])

  case class VenueShow(venue: Venue, show: Show)

}
