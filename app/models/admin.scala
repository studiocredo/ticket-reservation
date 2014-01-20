package models

import models.entities._
import models.ids._
import org.joda.time.DateTime
import models.entities.SeatType.SeatType

object admin {
  case class RichUser(user: User, detail: UserDetail) {
    def id = user.id
    def name = user.name
    def username = user.username
    def password = user.password
    def email = detail.email
    def address = detail.address
    def phone = detail.phone
  }

  case class UserFormData(name: String, username: String, email: Option[String], address: Option[String], phone: Option[String])


  case class EventDetail(event: Event, shows: List[VenueShows]) {
    def id = event.id
  }

  case class ShowEdit(venueId: VenueId, date: DateTime)

  case class VenueShows(venue: Venue, shows: List[Show])

  case class VenueShow(venue: Venue, show: Show)

  case class VenueCapacity(venue: Venue) {
    def total: Int = {
      venue.floorplan match {
        case Some(floorplan) => floorplan.rows.map{ _.content.count{ _.isInstanceOf[Seat]} }.sum
        case None => 0
      }
    }

    def byType(seatType: SeatType): Int = {
      venue.floorplan match {
        case Some(floorplan) => floorplan.rows.map{ _.content.count{ _ match { case seat:Seat => seat.kind == seatType ; case _ => false} } }.sum
        case None => 0
      }
    }
  }
}
