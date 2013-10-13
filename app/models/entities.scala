package models

object entities {

  class UserId(val id: Long) extends AnyVal
  case class User(
                   id: Option[UserId],
                   name: String,
                   email: String,
                   password: String
                 )

  // non member registered on site to buy tickets
  class GuestId(val Id: Long) extends AnyVal
  case class Guest(
                    id: Option[GuestId],
                    userId: UserId,
                    address: String,
                    phone: String
                  )
  class MemberId(val id: Long) extends AnyVal
  case class Member(id: Option[MemberId], userId: UserId, address: String, phone: String, active: Boolean)

  class AdminId(val id: Long) extends AnyVal
  case class Admin(id: Option[AdminId], userId: UserId)

  class CourseId(val id: Long) extends AnyVal
  // named group because class is a keyword (annoying)
  class GroupId(val id: Long) extends AnyVal
  class EventId(val id: Long) extends AnyVal
  class VenueId(val id: Long) extends AnyVal
  class ShowId(val id: Long) extends AnyVal
  class DvdId(val id: Long) extends AnyVal
  class OrderId(val id: Long) extends AnyVal
  class TicketOrderId(val id: Long) extends AnyVal
}
