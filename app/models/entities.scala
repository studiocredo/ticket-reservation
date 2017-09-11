package models

import java.io.{File, FileOutputStream}

import be.studiocredo.auth.{Password, Roles}
import be.studiocredo.util.Joda._
import be.studiocredo.util.Money
import controllers.EnumUtils
import models.admin.RichUser
import models.schema.PersistableEnumeration
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.data.FormError
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.mvc.{PathBindable, QueryStringBindable}

import scala.Predef._
import scala.slick.lifted.{BaseTypeMapper, MappedTypeMapper}
import scala.util.matching.Regex
import scala.util.{Success, Try}

object entities {

  import ids._

  object interfaces {

    trait Archiveable {
      val archived: Boolean
    }

    trait HasTime {
      val date: DateTime

      val isDone: Boolean = date.isBeforeNow
    }

    trait HasAmount {
      val amount: Money
    }

    trait Priced {
      val price: Money
    }

    trait MaybePriced {
      val price: Option[Money]
    }

  }

  import Roles._
  import interfaces._

  case class Identity(user: RichUser, roles: List[Role], otherUsers: List[User]) {
    def id: UserId = user.id

    def name: String = user.name

    def username: String = user.username

    def email: Option[String] = user.email

    def allUsers: List[UserId] = user.id :: otherUsers.map {
      _.id
    }
  }

  case class User(id: UserId, name: String, username: String, password: Password, loginGroupId: Option[UserId], active: Boolean)

  case class UserEdit(name: String, username: String, password: Password, active: Boolean)

  case class UserDetail(id: UserId, email: Option[String], address: Option[String], phone: Option[String])

  case class UserDetailEdit(email: Option[String], address: Option[String], phone: Option[String])

  case class UserRole(id: UserId, role: Roles.Role)

  case class Event(id: EventId, name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], template: Option[String], quota: Option[EventQuota], archived: Boolean) extends Archiveable {
    val preReservationAllowed: Boolean = preReservationStart.exists(!archived && _.isBeforeNow && preReservationEnd.exists(_.isAfterNow))
    val reservationAllowed: Boolean = reservationStart.forall(!archived && _.isBeforeNow && reservationEnd.exists(_.isAfterNow))
  }

  case class EventQuota(defaultValue: Int, values: Map[Int, Int] = Map()) {
    def quota(number: Int): Int = values.getOrElse(number, defaultValue)
  }

  object EventQuotaJson {

    import play.api.libs.functional.syntax._
    import play.api.libs.json._

    //no implicit conversion here as it is not generic enough
    val mapFmt: Format[Map[Int, Int]] = {
      new Format[Map[Int, Int]] {
        def writes(m: Map[Int, Int]): JsValue = {
          JsArray(m.toSeq.map { case (key, value) => JsObject(Seq("u" -> JsNumber(key), "v" -> JsNumber(value))) })
        }

        def reads(json: JsValue): JsResult[Map[Int, Int]] = {
          json.validate[Seq[Map[String, Int]]].map(_.map { newMap =>
            newMap("u") -> newMap("v")
          }.toMap)
        }
      }
    }

    implicit val eventQuotaReads: Reads[EventQuota] = (
      (__ \ 'default).read[Int] and
      (__ \ 'values).readNullable[Map[Int, Int]](mapFmt)
      )((default, maybeValues) => EventQuota(default, maybeValues.getOrElse(Map())))


    implicit val eventQuotaWrites: Writes[EventQuota] = new Writes[EventQuota] {
      override def writes(o: EventQuota): JsValue = {
        val default = Json.obj("default" -> o.defaultValue)
        Some(o.values).filter(_.nonEmpty).fold(default)(values => default ++ Json.obj("values" -> mapFmt.writes(values)))
      }
    }
  }

  object EventQuotaConstraints {
    def validUserQuota: Constraint[Map[Int, Int]] = Constraint[Map[Int, Int]]("constraint.event.userquota", Nil) { q =>
      validations.filter(!_._1(q)).map(_._2) match {
        case Nil => Valid
        case other => Invalid(other)
      }
    }

    def validEventQuota: Constraint[EventQuota] = Constraint[EventQuota]("constraint.event.quota", Nil) { q =>
      if (q.defaultValue >= q.values.values.max) {
        Valid
      } else {
        Invalid(ValidationError("error.event.quota.default_gt_values", Nil))
      }
    }

    private val validations: Seq[(Map[Int, Int] => Boolean, ValidationError)] = Seq(
      ((m: Map[Int, Int]) => m.keys == (1 to m.size).toSet, ValidationError("error.event.userquota.keys.consecutive", Nil)),
      ((m: Map[Int, Int])  => m.values.forall(_ > 0), ValidationError("error.event.userquota.values.postive", Nil)),
      ((m: Map[Int, Int])  => m.keys.toSeq.sorted.map(m(_)) == m.values.toSeq.sorted, ValidationError("error.event.userquota.values.increasing", Nil))
    )
  }

  case class EventEdit(name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], template: Option[String], quota: Option[EventQuota], archived: Boolean)

  object EventWithPriceEdit {
    def create(event: EventEdit, eventPricing: Option[EventPricing]) = EventWithPriceEdit(event.name, event.description, event.preReservationStart, event.preReservationEnd, event.reservationStart, event.reservationEnd, event.template, event.quota, event.archived, eventPricing.map(_.prices.map(ep => EventPriceEdit(ep.category, ep.price))))
  }
  case class EventWithPriceEdit(name: String, description: String, preReservationStart: Option[DateTime], preReservationEnd: Option[DateTime], reservationStart: Option[DateTime], reservationEnd: Option[DateTime], template: Option[String], quota: Option[EventQuota], archived: Boolean, pricing: Option[List[EventPriceEdit]]) {
    val event = EventEdit(name, description, preReservationStart, preReservationEnd, reservationStart, reservationEnd, template, quota, archived)
    def eventPricing(eventId: EventId): Option[EventPricing] = pricing.map{p => EventPricing(eventId, p.map(pe => EventPrice(eventId, pe.category, pe.price)))}
  }

  case class EventPrice(id: EventId, category: String, price: Money) extends Priced

  case class EventPriceEdit(category: String, price: Money) extends Priced

  case class EventPricing(id: EventId, prices: List[EventPrice])

  object SeatType extends Enumeration {
    type SeatType = Value
    val Normal: entities.SeatType.Value = Value("normal")
    val Vip: entities.SeatType.Value = Value("vip")
    val Disabled: entities.SeatType.Value = Value("disabled")
  }

  import SeatType._

  object SeatStatus extends Enumeration {
    type SeatStatus = Value
    val Free: entities.SeatStatus.Value = Value("free")
    val Reserved: entities.SeatStatus.Value = Value("reserved")
    val Unavailable: entities.SeatStatus.Value = Value("unavailable")
    val Mine: entities.SeatStatus.Value = Value("mine")
  }

  import SeatStatus._

  case class Venue(id: VenueId, name: String, description: String, floorplan: Option[FloorPlan], adminLabel: Option[String], archived: Boolean) extends Archiveable {
    val totalCapacity: Int = this.floorplan.map(_.rows.map {
      _.content.count {
        _.isInstanceOf[Seat]
      }
    }.sum).getOrElse(0)

    def capacityByType(seatType: SeatType): Int = {
      this.floorplan.map(_.rows.map {
        _.content.count { case seat: Seat => seat.kind == seatType; case _ => false }
      }.sum).getOrElse(0)
    }
  }

  case class VenueEdit(name: String, description: String, adminLabel: Option[String], archived: Boolean)

  case class Asset(id: AssetId, eventId: EventId, name: String, price: Option[Money], availableStart: DateTime, availableEnd: Option[DateTime], downloadable: Boolean, objectKey: Option[String], archived: Boolean) extends Archiveable with MaybePriced

  case class Show(id: ShowId, eventId: EventId, venueId: VenueId, date: DateTime, reservationStart: Option[DateTime], reservationEnd: Option[DateTime], archived: Boolean) extends Archiveable with HasTime {
    val reservationAllowed: Boolean = reservationStart.forall(!archived && _.isBeforeNow && reservationEnd.exists(_.isAfterNow))
  }

  case class EventShow(id: ShowId, eventId: EventId, name: String, venueId: VenueId, venueName: String, date: DateTime, template: Option[String], archived: Boolean) extends Archiveable with HasTime

  case class ShowAvailability(show: EventShow, byType: Map[SeatType, Int]) {
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

  case class Seat(id: SeatId, kind: SeatType, preference: Int) extends RowContent

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
      rows.flatMap(_.content).collectFirst { case seat: Seat if seat.id == seatId => seat }
    }

    def seatsWithStatus: List[SeatWithStatus] = {
      rows.flatMap(_.content).collect { case seat: SeatWithStatus => seat }
    }
  }

  object FloorPlanJson {

    import play.api.libs.json._

    implicit val seatTypeFmt: Format[entities.SeatType.Value] = EnumUtils.enumFormat(SeatType)
    implicit val seatStatusFmt: Format[entities.SeatStatus.Value] = EnumUtils.enumFormat(SeatStatus)
    implicit val seatIdFmt: Format[SeatId] = Json.format[SeatId]
    implicit val seatFmt: Format[Seat] = Json.format[Seat]
    implicit val seatWithStatusFmt: Format[SeatWithStatus] = Json.format[SeatWithStatus]
    implicit val spacerFmt: Format[Spacer] = Json.format[Spacer]

    implicit val rowContentFmt: Format[RowContent] = new Format[RowContent] {
      def reads(json: JsValue): JsResult[RowContent] = {
        json \ "ct" match {
          case JsString(RowContent.SEAT_TYPE) => Json.fromJson[Seat](json)(seatFmt)
          case JsString(RowContent.SEAT_STATUS_TYPE) => Json.fromJson[SeatWithStatus](json)(seatWithStatusFmt)
          case JsString(RowContent.SPACER_TYPE) => Json.fromJson[Spacer](json)(spacerFmt)
          case other => JsError(s"Unexpected json content '$other'")
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


    implicit val rowFmt: Format[Row] = Json.format[Row]
    implicit val floorPlanFmt: Format[FloorPlan] = Json.format[FloorPlan]
  }

  object OrderReference {
    val pattern: Regex = "\\b(\\d{3})[^\\d]?(\\d{3})(\\d{1})[^\\d]?(\\d{3})(\\d{2})\\b".r

    def parse(s: String): Option[OrderReference] = {
      pattern.findFirstMatchIn(s).flatMap { m =>
        val order = OrderId(m.group(1).toInt * 1000 + m.group(2).toInt)
        val user = UserId(m.group(3).toInt * 1000 + m.group(4).toInt)
        val remainder = m.group(5).toInt

        if (remainder == calcRemainder(order, user)) {
          Some(OrderReference(order, user))
        } else {
          None
        }
      }
    }

    def calcRemainder(order: OrderId, user: UserId): Long = {
      (order.id * 10000 + user.id) % 97 match { //9999 users and 999999 orders
        case 0 => 97
        case n => n
      }
    }
  }


  case class OrderReference(order: OrderId, user: UserId) {
    val remainder: Long = OrderReference.calcRemainder(order, user)
    val reference = s"${"%03d".format(order.id / 1000)}/${"%03d".format(order.id % 1000)}${"%01d".format(user.id / 1000)}/${"%03d".format(user.id % 1000)}${"%02d".format(remainder)}"
  }

  case class Order(id: OrderId, userId: UserId, date: DateTime, billingName: String, billingAddress: String, processed: Boolean, archived: Boolean, comments: Option[String]) {
    val orderReference = OrderReference(id, userId)

    def reference: String = {
      s"+++${orderReference.reference}+++"
    }
  }

  case class OrderEdit(userId: UserId, date: DateTime, billingName: String, billingAddress: String, processed: Boolean, archived: Boolean, comments: Option[String])

  case class OrderDetail(order: Order, user: User, ticketOrders: List[TicketOrderDetail]) extends Priced {
    val id: OrderId = order.id
    val price: Money = ticketOrders.map(_.price).foldLeft(Money(0))((total, amount) => total.plus(amount))
    val quantity: Int = ticketOrders.map(_.quantity).sum

    def quantityByShow(id: ShowId): Int = ticketSeatOrders.count(_.ticketSeatOrder.showId == id)

    val orderedTicketOrders: List[TicketOrderDetail] = ticketOrders.sortBy(_.show.date)
    val ticketSeatOrders: List[TicketSeatOrderDetail] = orderedTicketOrders.flatMap(_.ticketSeatOrders)

    def commentLines: List[String] = order.comments match {
      case None => List()
      case Some(comments) => comments.split("\n").toList
    }

    def billingAddressLines: List[String] = order.billingAddress.split("\n").toList

    val numberOfSeats: Int = ticketOrders.map(_.ticketSeatOrders.length).sum
    //val numberOfSeatsByShow = ticketOrders.flatMap(_.ticketSeatOrders.map((_.show.id, )))
  }

  case class OrderDetailEdit(userId: UserId, billingName: String, billingAddress: String, comments: Option[String], seats: List[TicketSeatOrderEdit])

  case class TicketSeatOrderEdit(ticketOrderId: TicketOrderId, seat: SeatId, price: Money) extends Priced

  case class OrderPayments(order: OrderDetail, payments: List[Payment]) {
    val balance: Money = payments.map(_.amount).foldLeft(order.price)((total, amount) => total.minus(amount))
    val isPaid: Boolean = balance.amount == 0
  }

  case class TicketOrder(id: TicketOrderId, orderId: OrderId, showId: ShowId)

  case class TicketOrderDetail(ticketOrder: TicketOrder, order: Order, show: EventShow, ticketSeatOrders: List[TicketSeatOrderDetail]) extends Priced {
    val id: TicketOrderId = ticketOrder.id

    val quantity: Int = ticketSeatOrders.length

    val price: Money = ticketSeatOrders.map(_.price).foldLeft(Money(0))((total, amount) => total.plus(amount))
  }

  case class TicketSeatOrder(ticketOrderId: TicketOrderId, showId: ShowId, userId: Option[UserId], seat: SeatId, price: Money) extends Priced

  case class TicketSeatOrderDetail(ticketSeatOrder: TicketSeatOrder, show: EventShow) extends Priced {
    val price: Money = ticketSeatOrder.price
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
    val notificationType: entities.NotificationType.Value = PendingPrereservation
  }

  case class UnusedQuotaNotificationEntry(subject: Event, value: Int, disabled: Boolean = false) extends NotificationEntry {
    val notificationType: entities.NotificationType.Value = UnusedQuota
  }

  case class DefaultNotificationEntry(subject: String, disabled: Boolean = false) extends NotificationEntry {
    val notificationType: entities.NotificationType.Value = Default
  }

  case class UserContext(notifications: List[Notification], otherUsers: List[User], isAdmin: Boolean) {
    val reservationAllowed: Boolean = isAdmin
    val preReservationAllowed: Boolean = isAdmin
  }

  object PaymentType extends PersistableEnumeration {
    type PaymentType = Value
    val Cash: entities.PaymentType.Value = Value("cash")
    val WireTransfer: entities.PaymentType.Value = Value("wire")
    val OnlineTransaction: entities.PaymentType.Value = Value("online")
  }

  import PaymentType._

  case class Payment(id: PaymentId, paymentType: PaymentType, importId: Option[String], orderId: Option[OrderId], debtor: String, amount: Money, message: Option[String], details: Option[String], date: DateTime, archived: Boolean) extends HasTime with Archiveable with HasAmount

  case class PaymentEdit(paymentType: PaymentType, importId: Option[String], orderId: Option[OrderId], debtor: String, amount: Money, message: Option[String], details: Option[String], date: DateTime, archived: Boolean) extends HasTime with Archiveable with HasAmount

  case class TicketDocument(order: OrderDetail, filename: String, pdf: Array[Byte], mimetype: String) {
    def saveAs(file: File) {
      val out = new FileOutputStream(file)
      try {
        out.write(pdf)
      } finally {
        out.close()
      }
    }
  }

  object TicketDistribution {
    val pattern: Regex = "^(\\d{4})-(\\d{5})-(\\d{17})-(\\d{3})$".r
    val datetimeformat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMddHHmmssSSS")

    def parse(s: String): Option[TicketDistribution] = {
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
    val remainder: Long = TicketDistribution.calcRemainder(order, serial)
    val reference = s"${"%04d".format(serial)}-${"%05d".format(order.id)}-${TicketDistribution.datetimeformat.print(date)}-${"%03d".format(remainder)}"
  }

  case class PriceCategory(key: String)

}

object ids {

  case class UserId(id: Long) extends AnyVal with TypedId

  case class AdminId(id: Long) extends AnyVal with TypedId

  case class EventId(id: Long) extends AnyVal with TypedId

  case class VenueId(id: Long) extends AnyVal with TypedId

  case class ShowId(id: Long) extends AnyVal with TypedId

  case class AssetId(id: Long) extends AnyVal with TypedId

  case class OrderId(id: Long) extends AnyVal with TypedId

  case class TicketOrderId(id: Long) extends AnyVal with TypedId

  case class PaymentId(id: Long) extends AnyVal with TypedId

  implicit object UserId extends IdFactory[UserId]

  implicit object AdminId extends IdFactory[AdminId]

  implicit object EventId extends IdFactory[EventId]

  implicit object VenueId extends IdFactory[VenueId]

  implicit object ShowId extends IdFactory[ShowId]

  implicit object AssetId extends IdFactory[AssetId]

  implicit object OrderId extends IdFactory[OrderId]

  implicit object TicketOrderId extends IdFactory[TicketOrderId]

  implicit object PaymentId extends IdFactory[PaymentId]


  trait TypedId extends Any {
    def id: Long

    override def toString: String = id.toString
  }

  implicit def idToLong(typedId: TypedId): Long = typedId.id

  implicit def idMapper[T <: TypedId](implicit create: IdFactory[T]): BaseTypeMapper[T] = MappedTypeMapper.base[T, Long](_.id, create)

  implicit def longToId[T <: TypedId](untypedId: Long)(implicit create: IdFactory[T]): T = create(untypedId)

  implicit def longToIdOption[T <: TypedId](untypedId: Long)(implicit create: IdFactory[T]): Option[T] = Option(create(untypedId))

  implicit def longToId[T <: TypedId](untypedId: Option[Long])(implicit create: IdFactory[T]): Option[T] = untypedId.map(create)

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

    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], T] = {
      Right(data.getOrElse(key, "false")).right.flatMap {
        case LongEx(i) => Right(create(i))
        case _ => Left(Seq(play.api.data.FormError(key, "error.id", Nil)))
      }
    }

    def unbind(key: String, untypedId: T) = Map(key -> untypedId.toString)
  }

  import play.api.data.FormError

  implicit val moneyFormatter: Formatter[Money] = new Formatter[Money] {
    override val format = Some(("format.money", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Money] = {
      data.get(key).map { value =>
        try {
          Right(Money(value.toDouble))
        } catch {
          case e: NumberFormatException => error(key, "error.money.invalid")
        }
      }.getOrElse(error(key, "error.money.missing"))
    }

    private def error(key: String, msg: String) = Left(List(FormError(key, msg)))

    override def unbind(key: String, money: Money) = Map(key -> money.amount.toString)
  }

  import models.entities.SeatType
  import models.entities.SeatType.SeatType

  implicit val seatTypeFormatter: Formatter[SeatType] = new Formatter[SeatType] {
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

    private def error(key: String, msg: String) = Left(List(FormError(key, msg)))

    override def unbind(key: String, value: SeatType): Map[String, String] = {
      Map(key -> value.toString)
    }
  }

  import models.entities.PaymentType
  import models.entities.PaymentType.PaymentType

  implicit val paymentTypeFormatter: Formatter[PaymentType] = new Formatter[PaymentType] {
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

    private def error(key: String, msg: String) = Left(List(FormError(key, msg)))

    override def unbind(key: String, value: PaymentType): Map[String, String] = {
      Map(key -> value.toString)
    }
  }

  import models.entities.SeatId

  implicit val seatFormatter: Formatter[SeatId] = new Formatter[SeatId] {
    override val format = Some(("format.seat", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], SeatId] = {
      data.get(key).map(value => Right(SeatId(value))).getOrElse(error(key, "error.seat.missing"))
    }

    private def error(key: String, msg: String) = Left(List(FormError(key, msg)))

    override def unbind(key: String, seat: SeatId) = Map(key -> seat.name)
  }

  //no implicit formatter here, because we only want to use this in specific cases
  val jsonMapFormatter: Formatter[Map[Int, Int]] = new Formatter[Map[Int, Int]] {
    override val format = Some(("format.event.userquota", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Map[Int, Int]] = {
      unmarshall(data.get(key)).transform(
        s => Success(Right(s)),
        f => Success(error(key, "error.invalid"))).get
    }

    override def unbind(key: String, value: Map[Int, Int]): Map[String, String] = {
      Map(key -> marshall(value))
    }

    private def error(key: String, msg: String) = Left(List(FormError(key, msg)))

    // 1:6, 2:4
    // all keys must be consecutive numbers starting from 1, values must increase with each user
    private def unmarshall(maybeInput: Option[String]): Try[Map[Int, Int]] = {
      maybeInput.filter(!"".equals(_)).fold(Success(Map()): Try[Map[Int, Int]]) { input =>
        Try {
          input.split(",").map(_.replaceAll(" ", "")).map{ element =>
            val splitElements = element.split(":", 2)
            Integer.parseInt(splitElements(0)) -> Integer.parseInt(splitElements(1))
          }.toMap
        }
      }
    }

    private def marshall(map: Map[Int, Int]): String = map.keys.toSeq.sorted.map { key => s"$key:${map(key)}" }.mkString(", ")
  }

  import play.api.libs.json._

  implicit val typeIdWrites: Writes[TypedId] = new Writes[TypedId] {
    def writes(c: TypedId): JsValue = JsNumber(c.id)
  }

  object TypedId {

    implicit def pathBinder[T <: TypedId](implicit create: IdFactory[T], longBinder: PathBindable[Long]): PathBindable[T] = new PathBindable[T] {
      override def bind(key: String, value: String): Either[String, T] = longBinder.bind(key, value).right.map(create(_))

      override def unbind(key: String, id: T): String = longBinder.unbind(key, id.id)
    }

    implicit def queryStringBinder[T <: TypedId](implicit create: IdFactory[T], longBinder: QueryStringBindable[Long]): QueryStringBindable[T] = new QueryStringBindable[T] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Serializable with Product with Either[String, T]] = longBinder.bind(key, params).map(_.right.map(create(_)))

      override def unbind(key: String, id: T): String = longBinder.unbind(key, id.id)
    }

  }

}
