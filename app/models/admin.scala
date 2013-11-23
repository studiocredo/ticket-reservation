package models

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
  }

  case class MemberFormData(name: String, username: String, email: Option[String], address: Option[String], phone: Option[String])


  case class EventDetail(event: Event, shows: Map[Venue, List[Show]]) {
    def id = event.id
  }

  case class UserMember(user: RichUser, member: Member) {
    def id = member.id
    def name = user.name
    def username = user.username
    def password = user.password
    def email = user.email
    def address = user.address
    def phone = user.phone
    def archived = member.archived
  }

  case class NewShow(venueId: VenueId, date:DateTime)
}
