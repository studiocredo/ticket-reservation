package models

import scala.Predef._
import scala.slick.lifted.MappedTypeMapper

object entities {

  import ids._

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


  case class Course(
                     id: Option[CourseId],
                     name: String,
                     active: Boolean
                   )

  case class Group(
                    id: Option[GroupId],
                    name: String,
                    year: Int,
                    course: CourseId
                  )
}

object ids {
  case class UserId(id: Long) extends AnyVal with TypedId
  case class AdminId(id: Long) extends AnyVal with TypedId
  case class GuestId(id: Long) extends AnyVal with TypedId
  case class MemberId(id: Long) extends AnyVal with TypedId
  case class CourseId(id: Long) extends AnyVal with TypedId
  case class GroupId(id: Long) extends AnyVal with TypedId
  case class EventId(id: Long) extends AnyVal with TypedId
  case class VenueId(id: Long) extends AnyVal with TypedId
  case class ShowId(id: Long) extends AnyVal with TypedId
  case class DvdId(id: Long) extends AnyVal with TypedId
  case class OrderId(id: Long) extends AnyVal with TypedId
  case class TicketOrderId(id: Long) extends AnyVal with TypedId

  implicit object UserId extends IdFactory[UserId]
  implicit object AdminId extends IdFactory[AdminId]
  implicit object GuestId extends IdFactory[GuestId]
  implicit object MemberId extends IdFactory[MemberId]
  implicit object CourseId extends IdFactory[CourseId]
  implicit object GroupId extends IdFactory[GroupId]
  implicit object EventId extends IdFactory[EventId]
  implicit object VenueId extends IdFactory[VenueId]
  implicit object ShowId extends IdFactory[ShowId]
  implicit object DvdId extends IdFactory[DvdId]
  implicit object OrderId extends IdFactory[OrderId]
  implicit object TicketOrderId extends IdFactory[TicketOrderId]


  trait TypedId extends Any {
    def id: Long
    override def toString: String = id.toString
  }

  implicit def idToLong(typedId: TypedId): Long = typedId.id

  implicit def idMapper[T <: TypedId](implicit create: IdFactory[T]) = MappedTypeMapper.base[T, Long](_.id, create)
  implicit def longToId[T <: TypedId](untypedId: Long)(implicit create: IdFactory[T]) = create(untypedId)
  implicit def longToIdOption[T <: TypedId](untypedId: Long)(implicit create: IdFactory[T]) = Option(create(untypedId))
  implicit def longToId[T <: TypedId](untypedId: Option[Long])(implicit create: IdFactory[T]) = untypedId.map(create)

  sealed trait IdFactory[T <: TypedId] extends (Long => T)


  // play custom id formatters
  import play.api.data.format.Formatter
  object LongEx {
    def unapply(s: String): Option[Long] = try {
      Some(s.toLong)
    } catch {
      case _: java.lang.NumberFormatException => None
    }
  }
  implicit def idFormatter[T <: TypedId](implicit create: IdFactory[T]): Formatter[T] = new Formatter[T] {
    override val format = Some(("format.id", Nil))

    def bind(key: String, data: Map[String, String]) = {
      Right(data.get(key).getOrElse("false")).right.flatMap {
        case LongEx(i) => Right(create(i))
        case _ => Left(Seq(play.api.data.FormError(key, "error.id", Nil)))
      }
    }

    def unbind(key: String, untypedId: T) = Map(key -> untypedId.toString)
  }
}
