package models

import scala.Predef._
import scala.slick.lifted.MappedTypeMapper
import org.joda.time.DateTime
import be.studiocredo.auth.{Roles, Password}
import models.admin.RichUser
import controllers.EnumUtils
import play.api.mvc.{PathBindable, QueryStringBindable}
import models.ids.{ShowId, TicketOrderId, OrderId}
import scala.Option
import be.studiocredo.util.Money
import models.admin.RichUser
import scala.Some
import be.studiocredo.auth.Password

object entities {

  import ids._

  object interfaces {

    trait Archiveable {
      def archived: Boolean
    }

    trait HasTime {
      def date: DateTime

      def isDone: Boolean = date.isBeforeNow
    }
  }

  import interfaces._
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

  case class UserRole(id: UserId, role: Roles.Role)

  case class Event(id: EventId, name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], archived: Boolean) extends Archiveable
  case class EventEdit(         name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], archived: Boolean)

  case class VenueEdit(         name: String, description: String, archived: Boolean)

  object SeatType extends Enumeration {
    type SeatType = Value
    val Normal = Value("normal")
    val Vip = Value("vip")
    val Disabled = Value("disabled")
  }

  import SeatType._

  case class Venue(id: VenueId, name: String, description: String, floorplan: Option[FloorPlan], archived: Boolean) extends Archiveable {
      def totalCapacity: Int = {
        this.floorplan match {
          case Some(floorplan) => floorplan.rows.map{ _.content.count{ _.isInstanceOf[Seat]} }.sum
          case None => 0
        }
      }

      def capacityByType(seatType: SeatType): Int = {
        this.floorplan match {
          case Some(floorplan) => floorplan.rows.map{ _.content.count{ _ match { case seat:Seat => seat.kind == seatType ; case _ => false} } }.sum
          case None => 0
        }
      }
    }

  case class Show(id: ShowId, eventId: EventId, venueId: VenueId, date: DateTime, archived: Boolean) extends Archiveable with HasTime

  case class ShowAvailability(show: Show, byType: Map[SeatType, Int] ) {
    def total: Int = {
      byType.values.sum
    }
  }

  case class ShowOverview(name: String, date: DateTime, showId: ShowId, eventId: EventId, availability: ShowAvailability)

  sealed trait RowContent
  case class SeatId(name: String)
  case class Seat(id: SeatId, kind: SeatType) extends RowContent
  case class Spacer(width: Int) extends RowContent
  object RowContent {
    val SEAT_TYPE = "seat"
    val SPACER_TYPE = "spacer"
  }

  case class Row(content: List[RowContent], vspace: Int)
  case class FloorPlan(rows: List[Row]) {
    def seat(seatId: SeatId): Option[Seat] = {
      rows.map{_.content}.flatten.collect{case seat:Seat => seat}.find{_.id == seatId}
    }
  }

  object FloorPlanJson {

    import play.api.libs.json._
    import play.api.libs.functional.syntax._

    implicit val seatTypeFmt = EnumUtils.enumFormat(SeatType)
    implicit val seatIdFmt = Json.format[SeatId]
    implicit val seatFmt = Json.format[Seat]
    implicit val spacerFmt = Json.format[Spacer]

    implicit val rowContentFmt: Format[RowContent] = new Format[RowContent] {
      def reads(json: JsValue): JsResult[RowContent] = {
        json \ "ct" match {
          case JsString(RowContent.SEAT_TYPE) => Json.fromJson[Seat](json)(seatFmt)
          case JsString(RowContent.SPACER_TYPE) => Json.fromJson[Spacer](json)(spacerFmt)
          case other  => JsError(s"Unexpected json content '$other'")
        }
      }

      def writes(content: RowContent): JsValue = {
        content match {
          case b: Seat => toJson(RowContent.SEAT_TYPE, Json.toJson(b)(seatFmt))
          case b: Spacer => toJson(RowContent.SPACER_TYPE, Json.toJson(b)(spacerFmt))
        }
      }

      def toJson(kind: String, b: JsValue): JsValue = {
        b match {
          case obj: JsObject => Json.obj("ct" -> kind) ++ obj
          case other => throw new IllegalArgumentException
        }
      }
    }


    implicit val rowFmt = Json.format[Row]
    implicit val floorPlanFmt = Json.format[FloorPlan]
  }

  case class Order(id: OrderId, userId: UserId, date: DateTime, billingName: String, billingAddress: String)
  case class TicketOrder(id: TicketOrderId, orderId: OrderId, showId: ShowId)
  case class TicketSeatOrder(ticketOrderId: TicketOrderId, showId: ShowId, userId: Option[UserId], seat: SeatId, price: Money)
}

object ids {
  case class UserId(id: Long) extends AnyVal with TypedId
  case class AdminId(id: Long) extends AnyVal with TypedId
  case class EventId(id: Long) extends AnyVal with TypedId
  case class VenueId(id: Long) extends AnyVal with TypedId
  case class ShowId(id: Long) extends AnyVal with TypedId
  case class DvdId(id: Long) extends AnyVal with TypedId
  case class OrderId(id: Long) extends AnyVal with TypedId
  case class TicketOrderId(id: Long) extends AnyVal with TypedId

  implicit object UserId extends IdFactory[UserId]
  implicit object AdminId extends IdFactory[AdminId]
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

  object TypedId {

    implicit def pathBinder[T <: TypedId](implicit create: IdFactory[T], longBinder: PathBindable[Long]): PathBindable[T] = new PathBindable[T] {
      override def bind(key: String, value: String) = longBinder.bind(key, value).right.map(create(_))

      override def unbind(key: String, id: T) = longBinder.unbind(key, id.id)
    }

    implicit def queryStringBinder[T <: TypedId](implicit create: IdFactory[T], longBinder: QueryStringBindable[Long]): QueryStringBindable[T] = new QueryStringBindable[T] {
      override def bind(key: String, params: Map[String, Seq[String]]) = longBinder.bind(key, params).map(_.right.map(create(_)))

      override def unbind(key: String, id: T) = longBinder.unbind(key, id.id)
    }

  }

}
