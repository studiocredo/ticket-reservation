package models

import scala.Predef._
import scala.slick.lifted.MappedTypeMapper
import org.joda.time.DateTime
import be.studiocredo.auth.{Roles, Password}
import models.admin.RichUser

object entities {

  import ids._

  object interfaces {

    trait Entity[T <: TypedId] {
      def id: T
    }

    trait Archiveable {
      def archived: Boolean
    }
  }
  import interfaces._
  // Well it's either an extra classes or using -1 id or be really annoyed with optional ids (that are always there except when used in forms), seem to hate this option the least at the moment

  import Roles._

  case class Identity(user: RichUser, roles: List[Role]) {
    def id = user.id
    def name = user.name
    def username = user.username
    def email = user.email
  }

  case class User(id: UserId, name: String, username: String, password: Password)
  case class UserEdit(        name: String, username: String, password: Password)

  case class UserDetail(id: UserId, email: Option[String], address: Option[String], phone: Option[String])
  case class UserDetailEdit(        email: Option[String], address: Option[String], phone: Option[String])

  case class Member(id: MemberId, userId: UserId, archived: Boolean) extends Entity[MemberId] with Archiveable
  case class MemberEdit(          userId: UserId, archived: Boolean)

  case class UserRole(id: UserId, role: Roles.Role)

  case class Course(id: CourseId, name: String, archived: Boolean) extends Entity[CourseId] with Archiveable
  case class CourseEdit(          name: String, archived: Boolean)

  case class Group(id: GroupId, name: String, year: Int, course: CourseId, archived: Boolean) extends Entity[GroupId] with Archiveable
  case class GroupEdit(         name: String, year: Int, course: CourseId, archived: Boolean)


  case class Event(id:EventId, name:String, description:String, archived: Boolean)  extends Entity[EventId] with Archiveable
  case class EventEdit(        name:String, description:String, archived: Boolean)

  case class Venue(id: VenueId, name:String, description:String, archived: Boolean) extends Entity[VenueId] with Archiveable
  case class VenueEdit(         name:String, description:String, archived: Boolean)

  case class Show(id: ShowId, eventId: EventId, venueId: VenueId, date:DateTime, archived: Boolean) extends Entity[ShowId] with Archiveable
  case class ShowEdit(        eventId: EventId, venueId: VenueId, date:DateTime, archived: Boolean)
}

object ids {
  case class UserId(id: Long) extends AnyVal with TypedId
  case class AdminId(id: Long) extends AnyVal with TypedId
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
