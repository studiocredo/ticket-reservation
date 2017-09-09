package models

import org.joda.time.DateTime
import play.api.db.slick.Config.driver.simple._
import com.github.tototoshi.slick.JodaSupport._
import be.studiocredo.auth.{AuthToken, EmailToken, Password, Roles}
import play.api.libs.json.Json
import be.studiocredo.util.Money
import models.admin.AssetEdit
import models.entities.PaymentType.PaymentType

object schema {

  object tables {

    val Users = new Users
    val UserDetails = new UserDetails
    val UserRoles = new UserRoles

    val Events = new Events
    val Venues = new Venues
    val Shows = new Shows
    val Assets = new Assets
    val EventPrices = new EventPrices

    val Orders = new Orders
    val TicketOrders = new TicketOrders
    val TicketSeatOrders = new TicketSeatOrders
    val Payments = new Payments
    val TicketDistributionLog = new TicketDistributionLog

    val ReservationQuota = new ReservationQuota
    val ShowPrereservations = new ShowPrereservations

    val AuthTokens = new AuthTokens
    val EmailAuthTokens = new EmailAuthTokens
  }

  import tables._
  import models.entities._
  import models.ids._

  trait Archiveable {
    this:Table[_] =>
    def archived = column[Boolean]("archived", O.Default(false))
  }

  abstract class PersistableEnumeration extends Enumeration {
    implicit val enumMapper = MappedTypeMapper.base[Value, String](_.toString, this.withName(_))
  }

  class Users extends Table[User]("user") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.DBType("TEXT"))
    def username = column[String]("username", O.DBType("TEXT"))
    def password = column[String]("password", O.DBType("TEXT"))
    def salt = column[String]("salt", O.DBType("TEXT"))
    def loginGroupId = column[Option[UserId]]("login-group_id")
    def active = column[Boolean]("active")

    def * = id ~ name ~ username ~ password ~ salt ~ loginGroupId ~ active <>(
      { (id, name, username, password, salt, loginGroupId, active) => User(id, name, username, Password(password, salt), loginGroupId, active)},
      {(user: User) => Some((user.id, user.name, user.username, user.password.hashed, user.password.salt, user.loginGroupId, user.active))}
      )

    def autoInc = name ~ username ~ password ~ salt ~ active <>(
      {(name, username, password, salt, active) => UserEdit(name, username, Password(password, salt), active)},
      {(user: UserEdit) => Some((user.name, user.username, user.password.hashed, user.password.salt, user.active))}
      ) returning id

    def uniqueUserName = index("idx_username", username, unique = true)
    def loginGroupFk = foreignKey("login-group_fk", loginGroupId, Users)(_.id)
  }

  class UserDetails extends Table[UserDetail]("user_detail") {
    def id = column[UserId]("id", O.PrimaryKey)
    def email = column[Option[String]]("email", O.DBType("TEXT"))
    def address = column[Option[String]]("address", O.DBType("TEXT"))
    def phone = column[Option[String]]("phone", O.DBType("TEXT"))

    def * = id ~ email ~ address ~ phone <>(UserDetail.apply _, UserDetail.unapply _)

    def user = foreignKey("user_fk", id, Users)(_.id)
  }

  class UserRoles extends Table[UserRole]("roles") {
    def userId = column[UserId]("id")
    def role = column[String]("role", O.DBType("TEXT"))

    def * = userId ~ role <>({(userId, role) => UserRole(userId, Roles.toRole(role))}, {(userRole:UserRole) => Some(userRole.id, Roles.toString(userRole.role))})

    def user = foreignKey("user_fk", userId, Users)(_.id)

    def unique = index("idx_userrole", userId ~ role, unique = true)
  }

  //////////////////////////////////

  class Events extends Table[Event]("event") with Archiveable {
    def id = column[EventId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.DBType("TEXT"))
    def description = column[String]("description", O.DBType("TEXT"))
    def preReservationStart = column[Option[DateTime]]("preReservationStart")
    def preReservationEnd = column[Option[DateTime]]("preReservationEnd")
    def reservationStart = column[Option[DateTime]]("reservationStart")
    def reservationEnd = column[Option[DateTime]]("reservationEnd")
    def template = column[Option[String]]("template", O.DBType("TEXT"))
    def quota = column[Option[EventQuota]]("quota", O.DBType("TEXT"))

    def * = id ~ name ~ description ~ preReservationStart ~ preReservationEnd ~ reservationStart ~ reservationEnd ~ template ~ quota ~ archived <>(Event.apply _, Event.unapply _)
    def edit = name ~ description ~ preReservationStart ~ preReservationEnd ~ reservationStart ~ reservationEnd ~ template ~ quota ~ archived <>(EventEdit.apply _, EventEdit.unapply _)
    def autoInc = edit returning id
  }

  class EventPrices extends Table[EventPrice]("event_pricing") {
    def eventId = column[EventId]("id")
    def priceCategory = column[String]("category", O.DBType("TEXT"))
    def price = column[Money]("price")

    def * = eventId ~ priceCategory ~ price <>(EventPrice.apply _, EventPrice.unapply _)

    def event = foreignKey("event_fk", eventId, Events)(_.id)

    def unique = index("idx_event_category", eventId ~ priceCategory, unique = true)
  }

  class Venues extends Table[Venue]("venue") with Archiveable {
    def id = column[VenueId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.DBType("TEXT"))
    def description = column[String]("description", O.DBType("TEXT"))
    def floorplan = column[Option[FloorPlan]]("floorplan", O.DBType("TEXT"))
    def adminLabel = column[Option[String]]("admin_label", O.DBType("TEXT"))


    def * = id ~ name ~ description ~ floorplan ~ adminLabel ~ archived <>(Venue.apply _, Venue.unapply _)

    def basic = name ~ description ~ adminLabel ~ archived
    def edit = basic <>(VenueEdit.apply _, VenueEdit.unapply _)
    def autoInc = edit returning id
  }

  class Shows extends Table[Show]("show") with Archiveable {
    def id = column[ShowId]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[EventId]("event_id")
    def venueId = column[VenueId]("venue_id")
    def reservationStart = column[Option[DateTime]]("reservationStart")
    def reservationEnd = column[Option[DateTime]]("reservationEnd")
    def date = column[DateTime]("date")

    def * = id ~ eventId ~ venueId ~ date ~ reservationStart ~ reservationEnd ~ archived <>(Show.apply _, Show.unapply _)
    def edit = venueId ~ date ~ reservationStart ~ reservationEnd ~ archived
    def autoInc = (eventId ~ venueId ~ date ~ reservationStart ~ reservationEnd ~ archived) returning id

    def event = foreignKey("event_fk", eventId, Events)(_.id)
    def venue = foreignKey("venue_fk", venueId, Venues)(_.id)
  }

  class Assets extends Table[Asset]("asset") with Archiveable {
    def id = column[AssetId]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[EventId]("event_id")
    def name = column[String]("name", O.DBType("TEXT"))
    def price = column[Option[Money]]("price")
    def availableStart = column[DateTime]("available-start")
    def availableEnd = column[Option[DateTime]]("available-end")
    def downloadable = column[Boolean]("downloadable")
    def objectKey = column[Option[String]]("object-key")

    def edit = name ~ price ~ availableStart ~ availableEnd ~ downloadable ~ objectKey ~ archived <>(AssetEdit.apply _, AssetEdit.unapply _)
    def autoInc = eventId ~ name ~ price ~ availableStart ~ availableEnd ~ downloadable ~ objectKey ~ archived returning id

    def * = id ~ eventId ~ name ~ price ~ availableStart ~ availableEnd ~ downloadable ~ objectKey ~ archived <>(Asset.apply _, Asset.unapply _)

    def event = foreignKey("event_fk", eventId, Events)(_.id)
  }

  //////////////////////////////////

  class Orders extends Table[Order]("order") with Archiveable {
    def id = column[OrderId]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[UserId]("user_id")
    def date = column[DateTime]("date")
    def billingName = column[String]("billing-name", O.DBType("TEXT"))
    def billingAddress = column[String]("billing-address", O.DBType("TEXT"))
    def processed = column[Boolean]("processed")
    def comments = column[Option[String]]("comments", O.DBType("TEXT"))

    def * = id ~ userId ~ date ~ billingName ~ billingAddress ~ processed ~ archived ~ comments <>(Order.apply _, Order.unapply _)

    def edit = userId ~ date ~ billingName ~ billingAddress ~ processed ~ archived ~ comments <>(OrderEdit.apply _, OrderEdit.unapply _)
    def autoInc = edit returning id

    def billingEdit = billingName ~ billingAddress
    def billingCommentsEdit = userId ~ billingName ~ billingAddress ~ comments

    def user = foreignKey("user_fk", userId, Users)(_.id)
  }

  class TicketOrders extends Table[TicketOrder]("order-ticket") {
    def id = column[TicketOrderId]("id", O.PrimaryKey, O.AutoInc)
    def orderId = column[OrderId]("order_id")
    def showId = column[ShowId]("show_id")
    // delivery method
    def * = id ~ orderId ~ showId <>(TicketOrder.apply _, TicketOrder.unapply _)

    def autoInc = orderId ~ showId returning id

    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def order = foreignKey("order_fk", orderId, Orders)(_.id)
  }

  class TicketSeatOrders extends Table[TicketSeatOrder]("order-ticket-seat") {
    def ticketOrderId = column[TicketOrderId]("ticket_order_id")
    def showId = column[ShowId]("show_id")
    def userId = column[Option[UserId]]("user_id")

    def seat = column[SeatId]("seat")

    def price = column[Money]("price")

    def * = ticketOrderId ~ showId ~ userId ~ seat ~ price <>(TicketSeatOrder.apply _, TicketSeatOrder.unapply _)

    //def pk = primaryKey("order-ticket-seat_pkey", (ticketOrderId, showId))

    def ticketOrder = foreignKey("ticket_order_fk", ticketOrderId, TicketOrders)(_.id)
    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def user = foreignKey("user_fk", userId, Users)(_.id)

    def unqiueSeats = index("idx_showseat", showId ~ seat, unique = true)
  }

  class ReservationQuota extends Table[ReservationQuotum]("reservation-quota") {
    def eventId = column[EventId]("event_id")
    def userId = column[UserId]("user_id")

    def quota = column[Int]("quota")

    def * = eventId ~ userId ~ quota <>(ReservationQuotum.apply _, ReservationQuotum.unapply _)

    def pk = primaryKey("event-user_pkey", (eventId, userId))

    def event = foreignKey("event_fk", eventId, Events)(_.id)
    def user = foreignKey("user_fk", userId, Users)(_.id)

    def unqiueEventUser = index("idx_eventuser", eventId ~ userId, unique = true)
  }

  class ShowPrereservations extends Table[ShowPrereservation]("show-prereservations") {
    def showId = column[ShowId]("show_id")
    def userId = column[UserId]("user_id")

    def quantity = column[Int]("quantity")

    def * = showId ~ userId ~ quantity <>(ShowPrereservation.apply _, ShowPrereservation.unapply _)

    def pk = primaryKey("show-user_pkey", (showId, userId))

    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def user = foreignKey("user_fk", userId, Users)(_.id)

    def uniqueShowUser = index("idx_showuser", showId ~ userId, unique = true)
  }

  class Payments extends Table[Payment]("payment") with Archiveable {
    def id = column[PaymentId]("id", O.PrimaryKey, O.AutoInc)
    def paymentType = column[PaymentType]("payment_type", O.DBType("TEXT"))
    def importId = column[Option[String]]("import_id", O.DBType("TEXT"))
    def orderId = column[Option[OrderId]]("order_id")
    def debtor = column[String]("debtor", O.DBType("TEXT"))
    def amount = column[Money]("amount")
    def message = column[Option[String]]("message", O.DBType("TEXT"))
    def details = column[Option[String]]("details", O.DBType("TEXT"))
    def date = column[DateTime]("date")

    def * = id ~ paymentType ~ importId ~ orderId ~  debtor ~ amount ~ message ~ details ~ date ~ archived <> (Payment.apply _, Payment.unapply _)

    def autoInc = paymentType ~ importId ~ orderId ~ debtor ~ amount ~ message ~ details ~ date ~ archived <>(PaymentEdit.apply _, PaymentEdit.unapply _) returning id

    def pk = foreignKey("order_fk", orderId, Orders)(_.id)
    def uniqueImportId = index("idx_import_id", importId, unique = true)

  }

  class TicketDistributionLog extends Table[TicketDistribution]("ticket_distribution_log") {
    def orderId = column[OrderId]("order_id")
    def serial = column[Int]("serial")
    def date = column[DateTime]("date")

    def * = orderId ~ serial ~ date <> (TicketDistribution.apply _, TicketDistribution.unapply _)

    def pk = foreignKey("order_fk", orderId, Orders)(_.id)
    def uniqueReferenceOrderId = index("idx_order_id_serial", orderId ~ serial, unique = true)
  }

  //////////////////////////////////

  class AuthTokens extends Table[AuthToken]("auth_tokens") {
    def id = column[String]("id", O.PrimaryKey)
    def userId = column[UserId]("user_id")
    def creation = column[DateTime]("creation")
    def lastUsed = column[DateTime]("last_used")
    def expiration = column[DateTime]("expiration")

    def * = id ~ userId ~ creation ~ lastUsed ~ expiration <>(AuthToken.apply _, AuthToken.unapply _)
  }

  class EmailAuthTokens extends Table[EmailToken]("auth_tokens_email") {
    def id = column[String]("id", O.PrimaryKey)
    def email = column[String]("email", O.DBType("TEXT"))
    def userId = column[Option[UserId]]("user_id")
    def creation = column[DateTime]("creation")
    def lastUsed = column[DateTime]("last_used")
    def expiration = column[DateTime]("expiration")

    def * = id ~ email ~ userId ~ creation ~ lastUsed ~ expiration <>(EmailToken.apply _, EmailToken.unapply _)
  }


  implicit val userIdType = MappedTypeMapper.base[UserId, Long](_.id, new UserId(_))
  implicit val adminIdType = MappedTypeMapper.base[AdminId, Long](_.id, new AdminId(_))
  implicit val eventIdType = MappedTypeMapper.base[EventId, Long](_.id, new EventId(_))
  implicit val venueIdType = MappedTypeMapper.base[VenueId, Long](_.id, new VenueId(_))
  implicit val showIdType = MappedTypeMapper.base[ShowId, Long](_.id, new ShowId(_))
  implicit val assetIdType = MappedTypeMapper.base[AssetId, Long](_.id, new AssetId(_))
  implicit val orderIdType = MappedTypeMapper.base[OrderId, Long](_.id, new OrderId(_))
  implicit val ticketOrderIdType = MappedTypeMapper.base[TicketOrderId, Long](_.id, new TicketOrderId(_))
  implicit val paymentIdType = MappedTypeMapper.base[PaymentId, Long](_.id, new PaymentId(_))

  implicit val moneyType = MappedTypeMapper.base[Money, BigDecimal](_.amount, Money(_))
  implicit val seatIdType = MappedTypeMapper.base[SeatId, String](_.name, SeatId(_))

  import FloorPlanJson._
  implicit val floorplanType = MappedTypeMapper.base[FloorPlan, String]({ plan => Json.stringify(Json.toJson(plan))}, { plan => Json.parse(plan).as[FloorPlan]})

  import EventQuotaJson._
  implicit val eventQuotaType = MappedTypeMapper.base[EventQuota, String]({ quota => Json.stringify(Json.toJson(quota))}, { quota => Json.parse(quota).as[EventQuota]})
}
