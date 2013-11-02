package models

import scala.Predef._
import scala.slick.lifted.MappedTypeMapper

object entities {

  import ids._

  // Well it's either an extra classes or using -1 id or be really annoyed with optional ids (that are always there except when used in forms), seem to hate this option the least at the moment
  case class User(id: UserId, email: String, password: String)
  case class UserEdit(        email: String, password: String)

  case class Guest(id: GuestId, userId: UserId, name: String, email: String, address: Option[String], phone: Option[String])
  case class GuestEdit(         userId: UserId, name: String, email: String, address: Option[String], phone: Option[String])


  case class Member(id: MemberId, name: String, email: Option[String], address: Option[String], phone: Option[String], archived: Boolean)
  case class MemberEdit(          name: String, email: Option[String], address: Option[String], phone: Option[String], archived: Boolean)

  case class Admin(id: AdminId, userId: UserId, name: String)
  case class AdminEdit(         userId: UserId, name: String)


  case class Course(id: CourseId, name: String, archived: Boolean)
  case class CourseEdit(          name: String, archived: Boolean)

  case class Group(id: GroupId, name: String, year: Int, course: CourseId, archived: Boolean)
  case class GroupEdit(         name: String, year: Int, course: CourseId, archived: Boolean)

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

  import play.api.libs.json._

  implicit val creatureWrites = new Writes[TypedId] {
    def writes(c: TypedId): JsValue = JsNumber(c.id)
  }
}
