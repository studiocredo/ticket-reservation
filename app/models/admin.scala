package models

import models.entities._
import models.ids._
import org.joda.time.DateTime
import scala.collection.mutable
import play.api.templates.Html

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
    def prereservationsByShow: Map[EventShow, ShowPrereservationDetail] = {
      mutable.Map[EventShow, ShowPrereservationDetail]().toMap
    }

    def unusedPreReservationsByShow: Map[EventShow, Int] = {
      mutable.Map[EventShow, Int]().withDefaultValue(0).toMap
    }

    def quotaByEvent: Map[Event, ReservationQuotumDetail] = {
      mutable.Map[Event, ReservationQuotumDetail]().toMap
    }

    def unusedQuotaByEvent: Map[Event, Int] = {
      mutable.Map[Event, Int]().withDefaultValue(0).toMap
    }
  }

  case class UserFormData(name: String, username: String, email: Option[String], address: Option[String], phone: Option[String], active: Boolean)


  case class EventDetail(event: Event, shows: List[VenueShows]) {
    def id = event.id
  }

  case class EventPrereservationsDetail(event: Event, users: List[User], shows: List[VenueShows], prereservations: List[ShowPrereservationDetail], reservationQuota: List[ReservationQuotumDetail]) {
    def id = event.id

    def totalQuota: Int = {
      reservationQuota.map{_.quota}.sum
    }

    def prereservationsByShow(showId: ShowId): Int = {
      prereservations.collect{case sprd if sprd.show.id == showId => sprd.quantity}.sum
    }
  }

  case class ShowEdit(venueId: VenueId, date: DateTime)

  case class VenueShows(venue: Venue, shows: List[Show])

  case class VenueShow(venue: Venue, show: Show)
}
