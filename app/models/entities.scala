package models

import scala.Predef._

object entities {
  case class User(
                   id: Option[UserId],
                   email: String,
                   password: String
                 )

  // non member registered on site to buy tickets

  case class Guest(
                    id: Option[GuestId],
                    userId: UserId,
                    name: String,
                    email: String,
                    address: Option[String],
                    phone: Option[String]
                  )

  case class Member(
                     id: Option[MemberId],
                     name: String,
                     email: Option[String],
                     address: Option[String],
                     phone: Option[String],
                     active: Boolean
                   )

  case class Admin(
                    id: Option[AdminId],
                    userId: UserId,
                    name: String
                  )

  class UserId(val id: Long) extends AnyVal
  class AdminId(val id: Long) extends AnyVal
  class GuestId(val id: Long) extends AnyVal
  class MemberId(val id: Long) extends AnyVal
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
