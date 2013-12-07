package models

import org.joda.time.DateTime
import play.api.db.slick.Config.driver.simple._
import com.github.tototoshi.slick.JodaSupport._
import be.studiocredo.auth.{Roles, EmailToken, AuthToken, Password}
import play.api.libs.json.Json
import play.api.Logger
import be.studiocredo.util.Money

object schema {

  object tables {

    val Users = new Users
    val UserDetails = new UserDetails
    val Members = new Members
    val UserRoles = new UserRoles

    val Events = new Events
    val Venues = new Venues
    val Shows = new Shows
    val Dvds = new Dvds
    val TicketReservations = new TicketReservations
    val Orders = new Orders
    val TicketOrders = new TicketOrders
    val TicketSeatOrders = new TicketSeatOrders

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

  class Users extends Table[User]("user") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.DBType("TEXT"))
    def username = column[String]("username", O.DBType("TEXT"))
    def password = column[String]("password", O.DBType("TEXT"))
    def salt = column[String]("salt", O.DBType("TEXT"))

    def * = id ~ name ~ username ~ password  ~ salt <>(
      { (id, name, username, password, salt) => User(id, name, username, Password(password, salt))},
      {(user: User) => Some((user.id, user.name, user.username, user.password.hashed, user.password.salt))}
      )

    def autoInc = name ~ username ~ password ~ salt <>(
      {(name, username, password, salt) => UserEdit(name, username, Password(password, salt))},
      {(user: UserEdit) => Some((user.name, user.username, user.password.hashed, user.password.salt))}
      ) returning id

    def uniqueUserName = index("idx_username", username, unique = true)
  }

  class UserDetails extends Table[UserDetail]("user_detail") {
    def id = column[UserId]("id", O.PrimaryKey)
    def email = column[Option[String]]("email", O.DBType("TEXT"))
    def address = column[Option[String]]("address", O.DBType("TEXT"))
    def phone = column[Option[String]]("phone", O.DBType("TEXT"))

    def * = id ~ email ~ address ~ phone <>(UserDetail.apply _, UserDetail.unapply _)

    def user = foreignKey("user_fk", id, Users)(_.id)
  }

  class Members extends Table[Member]("member") with Archiveable {
    def id = column[MemberId]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[UserId]("user_id")

    def * = id ~ userId ~ archived <>(Member, Member.unapply _)
    def edit = userId ~ archived <>(MemberEdit, MemberEdit.unapply _)
    def autoInc = edit returning id

    def user = foreignKey("user_fk", userId, Users)(_.id)
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

    def * = id ~ name ~ description ~ archived <>(Event.apply _, Event.unapply _)
    def edit = name ~ description ~ archived <>(EventEdit.apply _, EventEdit.unapply _)
    def autoInc = edit returning id
  }

  class Venues extends Table[Venue]("venue") with Archiveable {
    def id = column[VenueId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.DBType("TEXT"))
    def description = column[String]("description", O.DBType("TEXT"))
    def floorplan = column[Option[FloorPlan]]("floorplan", O.DBType("TEXT"))


    def * = id ~ name ~ description ~ floorplan ~ archived <>(Venue.apply _, Venue.unapply _)

    def basic = name ~ description ~ archived
    def edit = basic <>(VenueEdit.apply _, VenueEdit.unapply _)
    def autoInc = edit returning id
  }

  class Shows extends Table[Show]("show") with Archiveable {
    def id = column[ShowId]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[EventId]("event_id")
    def venueId = column[VenueId]("venue_id")
    def date = column[DateTime]("date")

    def * = id ~ eventId ~ venueId ~ date ~ archived <>(Show.apply _, Show.unapply _)
    def edit = eventId ~ venueId ~ date ~ archived <>(ShowEdit.apply _, ShowEdit.unapply _)
    def autoInc = edit returning id

    def event = foreignKey("event_fk", eventId, Events)(_.id)
    def venue = foreignKey("venue_fk", venueId, Venues)(_.id)
  }

  class Dvds extends Table[(DvdId, EventId, String, Int, DateTime, Option[DateTime], Boolean)]("dvd") {
    def id = column[DvdId]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[EventId]("event_id")
    def name = column[String]("name", O.DBType("TEXT"))
    def price = column[Int]("price")
    def availableStart = column[DateTime]("available-start")
    def availableEnd = column[Option[DateTime]]("available-end")
    def archived = column[Boolean]("archived", O.Default(false))

    def * = id ~ eventId ~ name ~ price ~ availableStart ~ availableEnd ~ archived

    def event = foreignKey("event_fk", eventId, Events)(_.id)
  }

  class TicketReservations extends Table[(ShowId, MemberId, Int)]("ticket-reservation") {
    def showId = column[ShowId]("show_id")
    def memberId = column[MemberId]("member_id")
    def amount = column[Int]("amount")

    def * = showId ~ memberId ~ amount

    def pk = primaryKey("ticket-reservation_pkey", (showId, memberId))
    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def member = foreignKey("member_fk", memberId, Members)(_.id)
  }

  //////////////////////////////////

  class Orders extends Table[(OrderId, UserId, DateTime, String, String)]("order") {
    def id = column[OrderId]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[UserId]("user_id")
    def date = column[DateTime]("date")
    def billingName = column[String]("billing-name", O.DBType("TEXT"))
    def billingAddress = column[String]("billing-address", O.DBType("TEXT"))

    def * = id ~ userId ~ date ~ billingName ~ billingAddress

    def user = foreignKey("user_fk", userId, Users)(_.id)
  }

  class TicketOrders extends Table[(TicketOrderId, OrderId, ShowId)]("order-ticket") {
    def id = column[TicketOrderId]("id", O.PrimaryKey, O.AutoInc)
    def orderId = column[OrderId]("order_id")
    def showId = column[ShowId]("show_id")
    // delivery method
    def * = id ~ orderId ~ showId

    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def order = foreignKey("order_fk", orderId, Orders)(_.id)
  }

  class TicketSeatOrders extends Table[(TicketOrderId, ShowId, Option[MemberId], Int, Int, Money)]("order-ticket-seat") {
    def ticketOrderId = column[TicketOrderId]("ticket_order_id")
    def showId = column[ShowId]("show_id")
    def memberId = column[Option[MemberId]]("member_id")

    def row = column[Int]("row")
    def seat = column[Int]("seat")

    def price = column[Money]("price")

    def * = ticketOrderId ~ showId ~ memberId ~ row ~ seat ~ price

    def pk = primaryKey("order-ticket-seat_pkey", (ticketOrderId, showId))

    def ticketOrder = foreignKey("ticket_order_fk", ticketOrderId, TicketOrders)(_.id)
    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def member = foreignKey("member_fk", memberId, Members)(_.id)

    def unqiueSeats = index("idx_showseat", showId ~ row ~ seat, unique = true)
  }

  class ReservationQuota extends Table[(EventId, MemberId, Int)]("reservation-quota") {
    def eventId = column[EventId]("event_id")
    def memberId = column[MemberId]("member_id")

    def quota = column[Int]("quota")

    def * = eventId ~ memberId ~ quota

    def pk = primaryKey("event-member_pkey", (eventId, memberId))

    def event = foreignKey("event_fk", eventId, Events)(_.id)
    def member = foreignKey("member_fk", memberId, Members)(_.id)
  }

  class ShowPrereservations extends Table[(ShowId, MemberId, Int)]("show-prereservations") {
    def showId = column[ShowId]("show_id")
    def memberId = column[MemberId]("member_id")

    def quantity = column[Int]("quantity")

    def * = showId ~ memberId ~ quantity

    def pk = primaryKey("show-member_pkey", (showId, memberId))

    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def member = foreignKey("member_fk", memberId, Members)(_.id)
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
  implicit val memberIdType = MappedTypeMapper.base[MemberId, Long](_.id, new MemberId(_))
  implicit val eventIdType = MappedTypeMapper.base[EventId, Long](_.id, new EventId(_))
  implicit val venueIdType = MappedTypeMapper.base[VenueId, Long](_.id, new VenueId(_))
  implicit val showIdType = MappedTypeMapper.base[ShowId, Long](_.id, new ShowId(_))
  implicit val dvdIdType = MappedTypeMapper.base[DvdId, Long](_.id, new DvdId(_))
  implicit val orderIdType = MappedTypeMapper.base[OrderId, Long](_.id, new OrderId(_))
  implicit val ticketOrderIdType = MappedTypeMapper.base[TicketOrderId, Long](_.id, new TicketOrderId(_))

  implicit val moneyType = MappedTypeMapper.base[Money, BigDecimal](_.amount, Money(_))

  import FloorPlanJson._
  implicit val floorplanType = MappedTypeMapper.base[FloorPlan, String]({ plan => Json.stringify(Json.toJson(plan))}, { plan => Json.parse(plan).as[FloorPlan]})
}
