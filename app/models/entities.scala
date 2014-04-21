package models

import scala.Predef._
import scala.slick.lifted.MappedTypeMapper
import org.joda.time.DateTime
import be.studiocredo.auth.Roles
import controllers.EnumUtils
import play.api.mvc.{PathBindable, QueryStringBindable}
import scala.{Int, Option, Some}
import be.studiocredo.util.Money
import models.admin.RichUser
import be.studiocredo.auth.Password
import models.schema.PersistableEnumeration
import java.io.{FileOutputStream, File}
import org.joda.time.format.DateTimeFormat
import play.api.http.MimeTypes

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

  case class Identity(user: RichUser, roles: List[Role], otherUsers: List[User]) {
    def id = user.id
    def name = user.name
    def username = user.username
    def email = user.email

    def allUsers = user.id :: otherUsers.map { _.id }
  }

  case class User(id: UserId, name: String, username: String, password: Password, loginGroupId: Option[UserId], active: Boolean)
  case class UserEdit(        name: String, username: String, password: Password, active: Boolean)

  case class UserDetail(id: UserId, email: Option[String], address: Option[String], phone: Option[String])
  case class UserDetailEdit(        email: Option[String], address: Option[String], phone: Option[String])

  case class UserRole(id: UserId, role: Roles.Role)

  case class Event(id: EventId, name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], archived: Boolean) extends Archiveable {
    def preReservationAllowed = preReservationStart match {
      case Some(preReservationStart) => !archived && preReservationStart.isBeforeNow && preReservationEnd.get.isAfterNow
      case None => true
    }
    def reservationAllowed = reservationStart match {
      case Some(reservationStart) => !archived && reservationStart.isBeforeNow && reservationEnd.get.isAfterNow
      case None => true
    }
  }
  case class EventEdit(         name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], archived: Boolean)

  case class EventPrice(id: EventId, category: String, price: Money)
  case class EventPricing(id: EventId, prices: List[EventPrice])

  object SeatType extends Enumeration {
    type SeatType = Value
    val Normal = Value("normal")
    val Vip = Value("vip")
    val Disabled = Value("disabled")
  }
  import SeatType._

  object SeatStatus extends Enumeration {
    type SeatStatus = Value
    val Free = Value("free")
    val Reserved = Value("reserved")
    val Unavailable = Value("unavailable")
    val Mine = Value("mine")
  }
  import SeatStatus._

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
  case class VenueEdit(         name: String, description: String, archived: Boolean)

  case class Show(id: ShowId, eventId: EventId, venueId: VenueId, date: DateTime, archived: Boolean) extends Archiveable with HasTime
  case class EventShow(id: ShowId, eventId: EventId, name: String, venueId: VenueId, venueName: String, date: DateTime, archived: Boolean) extends Archiveable with HasTime

  case class ShowAvailability(show: EventShow, byType: Map[SeatType, Int] ) {
    def total: Int = {
      byType.values.sum
    }
  }

  case class UserPendingPrereservations(user: RichUser, pending: Int)
  case class DetailedShowAvailability(availability: ShowAvailability, pending: List[UserPendingPrereservations], freeByType: Map[SeatType, Int]) {
    def totalPending: Int = {
      pending.map(_.pending).sum
    }
    def totalFree: Int = {
      freeByType.values.sum
    }
  }


  sealed trait RowContent
  case class SeatId(name: String)
  case class Seat          (id: SeatId, kind: SeatType, preference: Int) extends RowContent
  case class SeatWithStatus(id: SeatId, kind: SeatType, status: SeatStatus, preference: Int, comment: Option[String] = None) extends RowContent
  case class Spacer(width: Int) extends RowContent
  object RowContent {
    val SEAT_TYPE = "seat"
    val SEAT_STATUS_TYPE = "seat-status"
    val SPACER_TYPE = "spacer"
  }

  case class Row(content: List[RowContent], vspace: Int)
  case class FloorPlan(rows: List[Row]) {
    def seat(seatId: SeatId): Option[Seat] = {
      rows.map{_.content}.flatten.collectFirst{case seat:Seat if seat.id == seatId => seat}
    }
    def seatsWithStatus: List[SeatWithStatus] = {
      rows.map{_.content}.flatten.collect{case seat:SeatWithStatus => seat}
    }
  }

  object FloorPlanJson {

    import play.api.libs.json._

    implicit val seatTypeFmt = EnumUtils.enumFormat(SeatType)
    implicit val seatStatusFmt = EnumUtils.enumFormat(SeatStatus)
    implicit val seatIdFmt = Json.format[SeatId]
    implicit val seatFmt = Json.format[Seat]
    implicit val seatWithStatusFmt = Json.format[SeatWithStatus]
    implicit val spacerFmt = Json.format[Spacer]

    implicit val rowContentFmt: Format[RowContent] = new Format[RowContent] {
      def reads(json: JsValue): JsResult[RowContent] = {
        json \ "ct" match {
          case JsString(RowContent.SEAT_TYPE) => Json.fromJson[Seat](json)(seatFmt)
          case JsString(RowContent.SEAT_STATUS_TYPE) => Json.fromJson[SeatWithStatus](json)(seatWithStatusFmt)
          case JsString(RowContent.SPACER_TYPE) => Json.fromJson[Spacer](json)(spacerFmt)
          case other  => JsError(s"Unexpected json content '$other'")
        }
      }

      def writes(content: RowContent): JsValue = {
        content match {
          case b: Seat => toJson(RowContent.SEAT_TYPE, Json.toJson(b)(seatFmt))
          case b: SeatWithStatus => toJson(RowContent.SEAT_STATUS_TYPE, Json.toJson(b)(seatWithStatusFmt))
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

  object OrderReference {
    val pattern = "\\b(\\d{3})[^\\d]?(\\d{4})[^\\d]?(\\d{5})\\b".r
    def parse(s: String) = {
      pattern.findFirstMatchIn(s).flatMap { m =>
        val remainder = m.group(1).toInt
        val user = UserId(m.group(2).toInt)
        val order = OrderId(m.group(3).toInt)

        if (remainder == calcRemainder(order, user)) {
          Some(OrderReference(order, user))
        } else {
          None
        }
      }
    }

    def calcRemainder(order: OrderId, user: UserId): Long = {
      (order.id * 10000 + user.id) % 997
    }
  }



  case class OrderReference(order: OrderId, user: UserId) {
    val remainder = OrderReference.calcRemainder(order, user)
    val reference = s"${"%03d".format(remainder)}/${"%04d".format(user.id)}/${"%05d".format(order.id)}"
  }

  case class Order(id: OrderId, userId: UserId, date: DateTime, billingName: String, billingAddress: String, processed: Boolean, comments: Option[String]) {
    val orderReference = OrderReference(id, userId)

    def reference: String = {
      s"REF ${orderReference.reference}"
    }
  }
  case class OrderEdit(         userId: UserId, date: DateTime, billingName: String, billingAddress: String, processed: Boolean, comments: Option[String])
  case class OrderDetail(order: Order, user: User, ticketOrders: List[TicketOrderDetail]) {
    def id = order.id
    def price = ticketOrders.map(_.price).foldLeft(Money(0))((total, amount) => total.plus(amount))
    def quantity = ticketOrders.map(_.quantity).sum
    def quantityByShow(id: ShowId) = ticketSeatOrders.count(_.ticketSeatOrder.showId == id)
    def ticketSeatOrders = ticketOrders.flatMap(_.ticketSeatOrders)
    def commentLines: List[String] = order.comments match {
      case None => List()
      case Some(comments) => comments.split("\n").toList
    }
    def billingAddressLines: List[String] = order.billingAddress.split("\n").toList
    val numberOfSeats = ticketOrders.map(_.ticketSeatOrders.length).sum
    //val numberOfSeatsByShow = ticketOrders.flatMap(_.ticketSeatOrders.map((_.show.id, )))
  }
  case class OrderDetailEdit(billingName: String, billingAddress: String, comments: Option[String], seats: List[TicketSeatOrderEdit])
  case class TicketSeatOrderEdit(ticketOrderId: TicketOrderId, seat: SeatId, price: Money)

  case class OrderPayments(order: OrderDetail, payments: List[Payment]) {
    val balance = payments.map(_.amount).foldLeft(order.price)((total,amount) => total.minus(amount))
    val isPaid = balance.amount == 0
  }

  case class TicketOrder(id: TicketOrderId, orderId: OrderId, showId: ShowId)
  case class TicketOrderDetail(ticketOrder: TicketOrder, order: Order, show: EventShow, ticketSeatOrders: List[TicketSeatOrderDetail]) {
    def id = ticketOrder.id
    def quantity = ticketSeatOrders.length
    def price = ticketSeatOrders.map(_.price).foldLeft(Money(0))((total, amount) => total.plus(amount))
  }

  case class TicketSeatOrder(ticketOrderId: TicketOrderId, showId: ShowId, userId: Option[UserId], seat: SeatId, price: Money)
  case class TicketSeatOrderDetail(ticketSeatOrder: TicketSeatOrder, show: EventShow) {
    def price = ticketSeatOrder.price
  }

  case class ReservationQuotum(eventId: EventId, userId: UserId, quota: Int)
  case class ReservationQuotumDetail(event: Event, user: User, quota: Int)

  case class UnusedQuotaDisplay(eventMap: Map[Event, Int]) {
    def total: Int = {
      eventMap.values.sum
    }
  }

  case class ShowPrereservation(showId: ShowId, userId: UserId, quantity: Int)
  case class ShowPrereservationUpdate(showId: ShowId, quantity: Int)
  case class ShowPrereservationDetail(show: EventShow, user: User, quantity: Int)

  case class PendingPrereservationDisplay(showMap: Map[EventShow, Int]) {
    def total: Int = {
      showMap.values.sum
    }
  }

  object NotificationType extends Enumeration {
    type NotificationType = Value
    val PendingPrereservation, UnusedQuota, Default = Value
  }
  import NotificationType._

  sealed trait NotificationEntry {
    def notificationType: NotificationType
    def disabled: Boolean
  }

  case class Notification(title: String, entries: List[NotificationEntry])
  case class PendingPrereservationNotificationEntry(subject: EventShow, value: Int, disabled: Boolean = false) extends NotificationEntry {
    val notificationType = PendingPrereservation
  }
  case class UnusedQuotaNotificationEntry(subject: Event, value: Int, disabled: Boolean = false) extends NotificationEntry {
    val notificationType = UnusedQuota
  }
  case class DefaultNotificationEntry(subject: String, disabled: Boolean = false) extends NotificationEntry {
    val notificationType = Default
  }

  case class UserContext(notifications: List[Notification], otherUsers: List[User])

  object PaymentType extends PersistableEnumeration {
    type PaymentType = Value
    val Cash = Value("cash")
    val WireTransfer = Value("wire")
    val OnlineTransaction = Value("online")
  }
  import PaymentType._

  case class Payment(id: PaymentId, paymentType: PaymentType, importId: Option[String], orderId: Option[OrderId], debtor: String, amount: Money, message: Option[String], details: Option[String], date: DateTime, archived: Boolean) extends HasTime with Archiveable
  case class PaymentEdit(           paymentType: PaymentType, importId: Option[String], orderId: Option[OrderId], debtor: String, amount: Money, message: Option[String], details: Option[String], date: DateTime, archived: Boolean) extends HasTime with Archiveable

  case class TicketDocument(order: OrderDetail, filename: String, pdf: Array[Byte], mimetype: String) {
    def saveAs(file: File) {
      val out = new FileOutputStream(file)
      try { out.write(pdf) } finally { out.close() }
    }
  }

  object TicketDistribution {
    val pattern = "^(\\d{4})-(\\d{5})-(\\d{17})-(\\d{3})$".r
    val datetimeformat =  DateTimeFormat.forPattern("yyyyMMddHHmmssSSS")

    def parse(s: String) = {
      pattern.findFirstMatchIn(s).flatMap { m =>
        val serial = m.group(1).toInt
        val order = OrderId(m.group(2).toInt)
        val timestamp = datetimeformat.parseDateTime(m.group(3))
        val remainder = m.group(4).toInt

        if (remainder == calcRemainder(order, serial)) {
          Some(TicketDistribution(order, serial, timestamp))
        } else {
          None
        }
      }
    }

    def calcRemainder(order: OrderId, serial: Int): Long = {
      (order.id * 10000 + serial) % 997
    }
  }

  case class TicketDistribution(order: OrderId, serial: Int, date: DateTime) extends HasTime {
    val remainder = TicketDistribution.calcRemainder(order, serial)
    val reference = s"${"%04d".format(serial)}-${"%05d".format(order.id)}-${TicketDistribution.datetimeformat.print(date)}-${"%03d".format(remainder)}"
  }
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
  case class PaymentId(id: Long) extends AnyVal with TypedId

  implicit object UserId extends IdFactory[UserId]
  implicit object AdminId extends IdFactory[AdminId]
  implicit object EventId extends IdFactory[EventId]
  implicit object VenueId extends IdFactory[VenueId]
  implicit object ShowId extends IdFactory[ShowId]
  implicit object DvdId extends IdFactory[DvdId]
  implicit object OrderId extends IdFactory[OrderId]
  implicit object TicketOrderId extends IdFactory[TicketOrderId]
  implicit object PaymentId extends IdFactory[PaymentId]


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

  import play.api.data.FormError
  implicit val moneyFormatter = new Formatter[Money] {
    override val format = Some(("format.money", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Money] = {
      data.get(key).map { value =>
        try {
          Right(Money(value.toFloat))
        } catch {
          case e: NumberFormatException => error(key, "error.money.invalid")
        }
      }.getOrElse(error(key, "error.money.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, money: Money) = Map(key -> money.amount.toString)
  }

  import models.entities.SeatType
  import models.entities.SeatType.SeatType
  implicit val seatTypeFormatter = new Formatter[SeatType] {
    override val format = Some(("format.seatType", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], SeatType] = {
      data.get(key).map { value =>
        try {
          Right(SeatType.withName(value))
        } catch {
          case e: NoSuchElementException => error(key, "error.seatType.invalid")
        }
      }.getOrElse(error(key, "error.seatType.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, value: SeatType): Map[String, String] = {
      Map(key -> value.toString)
    }
  }

  import models.entities.PaymentType
  import models.entities.PaymentType.PaymentType
  implicit val paymentTypeFormatter = new Formatter[PaymentType] {
    override val format = Some(("format.paymentType", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], PaymentType] = {
      data.get(key).map { value =>
        try {
          Right(PaymentType.withName(value))
        } catch {
          case e: NoSuchElementException => error(key, "error.paymentType.invalid")
        }
      }.getOrElse(error(key, "error.paymentType.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, value: PaymentType): Map[String, String] = {
      Map(key -> value.toString)
    }
  }

  import models.entities.SeatId
  implicit val seatFormatter = new Formatter[SeatId] {
    override val format = Some(("format.seat", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], SeatId] = {
      data.get(key).map(value => Right(SeatId(value))).getOrElse(error(key, "error.seat.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, seat: SeatId) = Map(key -> seat.name)
  }

  import play.api.libs.json._

  implicit val typeIdWrites = new Writes[TypedId] {
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
