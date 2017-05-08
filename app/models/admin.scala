package models

import be.studiocredo.util.Joda._
import be.studiocredo.util.Money
import models.entities._
import models.ids._
import org.joda.time.DateTime

object admin {

  case class RichUser(user: User, detail: UserDetail) {
    def id = user.id

    def name = user.name

    def username = user.username

    def password = user.password

    def email = detail.email

    def address = detail.address

    def phone = detail.phone

    def addressLines: List[String] = address match {
      case None => List()
      case Some(address) => address.split("\n").toList
    }

    def active = user.active
  }

  case class RichUserWithReservationHistory(user: RichUser, otherUsers: List[User], orders: List[OrderDetail], prereservations: List[ShowPrereservationDetail], pendingPrereservations: PendingPrereservationDisplay, reservationQuota: List[ReservationQuotumDetail], unusedQuota: UnusedQuotaDisplay) {
    def prereservationsByShow: Map[EventShow, List[ShowPrereservationDetail]] = {
      prereservations.groupBy(_.show)
    }

    def unusedPreReservationsByShow: Map[EventShow, Int] = {
      pendingPrereservations.showMap
    }

    def shows: List[EventShow] = {
      (prereservations.map {
        _.show
      } ++ pendingPrereservations.showMap.keys).distinct.sortBy(_.date)
    }

    def events: List[Event] = {
      (reservationQuota.map {
        _.event
      } ++ unusedQuota.eventMap.keys).distinct
    }

    def quotaByEvent: Map[Event, List[ReservationQuotumDetail]] = {
      reservationQuota.groupBy(_.event)
    }

    def unusedQuotaByEvent: Map[Event, Int] = {
      unusedQuota.eventMap
    }
  }

  case class UserFormData(name: String, username: String, email: Option[String], address: Option[String], phone: Option[String], active: Boolean)


  case class EventDetail(event: Event, shows: List[VenueShows], pricing: Option[EventPricing], assets: List[Asset]) {
    def id = event.id

    def name = event.name

    def reservationAllowed = event.reservationAllowed

    def preReservationAllowed = event.preReservationAllowed

    def orderedShows = shows.flatMap(_.shows).sortBy(_.date)

    def orderedVenueShows = shows.flatMap(vs => vs.shows.map((vs, _))).sortBy(_._2.date)
  }

  case class EventPrereservationsDetail(event: Event, users: List[User], shows: List[VenueShows], prereservations: List[ShowPrereservationDetail], reservationQuota: Option[Int]) {
    def id = event.id

    def prereservationsByShow(showId: ShowId): Int = {
      prereservations.collect { case sprd if sprd.show.id == showId => sprd.quantity }.sum
    }

    def orderedShows = shows.flatMap { vs => vs.shows.map((vs.venue, _)) }.sortBy(_._2.date)
  }

  object EventReservationsDetail {
    val maxQuantityPerReservation = 10
  }

  case class EventReservationsDetail(event: EventDetail, users: List[User], shows: List[VenueShows], pendingPrereservationsByShow: Map[ShowId, Int]) {
    def id = event.id

    //TODO: WARNING the number of tickets per session should be at least equal
    //      to the total number of prereservations for that family otherwise
    //      they cannot order all their prereservations at once
    def totalQuota = users.length match {
      case 0 => 0
      case 1 => 5
      case 2 => 8
      case _ => 10
    } //TODO: make number of tickets per session configurable

    def orderedShows = shows.flatMap { vs => vs.shows.map((vs.venue, _)) }.sortBy(_._2.date)
  }

  case class ShowEdit(venueId: VenueId, date: DateTime)

  object RichAsset {
    def init(e: Event, a: Asset) = RichAsset(a.id, e, a.name, a.price, a.availableStart, a.availableEnd, a.downloadable, a.objectKey, a.archived)
  }
  case class RichAsset(id: AssetId, event: Event, name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean)

  case class AssetEdit(name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean)

  case class VenueShows(venue: Venue, shows: List[Show])

  case class VenueShow(venue: Venue, show: Show)

}
