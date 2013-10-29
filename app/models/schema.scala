package models

import org.joda.time.DateTime
import play.api.db.slick.Config.driver.simple._
import com.github.tototoshi.slick.JodaSupport._

object schema {

  object tables {

    val Users = new Users
    val Guests = new Guests
    val Members = new Members
    val Admins = new Admins
    val Courses = new Courses
    val Groups = new Groups
    val GroupMembers = new GroupMembers
    val Events = new Events
    val Venues = new Venues
    val Shows = new Shows
    val Dvds = new Dvds
    val EventParticipants = new EventParticipants
    val TicketReservations = new TicketReservations
    val Orders = new Orders
    val TicketOrders = new TicketOrders
    val TicketSeatOrders = new TicketSeatOrders
  }

  import tables._
  import models.entities._
  import models.ids._

  class Users extends Table[User]("user") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def password = column[String]("password")

    def * = id.? ~ email ~ password <>(User, User.unapply _)

    def forInsert = email ~ password <>( {
      (email, password) => User(None, email, password)
    }, {
      u: User => Some((u.email, u.password))
    })

    def uniqueEmail = index("idx_email", email, unique = true)
  }

  class Guests extends Table[Guest]("guest") {
    def id = column[GuestId]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[UserId]("user_id")
    def name = column[String]("name")
    def email = column[String]("email")
    def address = column[Option[String]]("address")
    def phone = column[Option[String]]("phone")

    def * = id.? ~ userId ~ name ~ email ~ address ~ phone <>(Guest, Guest.unapply _)

    def user = foreignKey("user_fk", userId, Users)(_.id)
  }

  class Members extends Table[Member]("member") {
    def id = column[MemberId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[Option[String]]("email")
    def address = column[Option[String]]("address")
    def phone = column[Option[String]]("phone")
    def active = column[Boolean]("active")

    def * = id.? ~ name ~ email ~ address ~ phone ~ active <>(Member, Member.unapply _)

    val byId = createFinderBy(t => t.id)

    def forInsert = name ~ email ~ address ~ phone ~ active <>( {
      (name, email, address, phone, active) => Member(None, name, email, address, phone, active)
    }, {
      u: Member => Some((u.name, u.email, u.address, u.phone, u.active))
    })
  }

  class Admins extends Table[Admin]("admin") {
    def id = column[AdminId]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[UserId]("user_id")
    def name = column[String]("name")

    def * = id.? ~ userId ~ name <>(Admin, Admin.unapply _)
    def forInsert = userId ~ name <>( {
      (userId, name) => Admin(None, userId, name)
    }, {
      u: Admin => Some((u.userId, u.name))
    })

    def user = foreignKey("user_fk", userId, Users)(_.id)
  }

  //////////////////////////////////

  class Courses extends Table[Course]("course") {
    def id = column[CourseId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def active = column[Boolean]("active")

    def * = id.? ~ name ~ active <>(Course.apply _, Course.unapply _)
    def forInsert = name ~ active <>( {
      (name, active) => Course(None, name, active)
    }, {
      u: Course => Some((u.name, u.active))
    })
  }

  class Groups extends Table[Group]("group") {
    def id = column[GroupId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def year = column[Int]("year")
    def courseId = column[CourseId]("course_id")

    def * = id.? ~ name ~ year ~ courseId <>(Group.apply _, Group.unapply _)
    def forInsert = name ~ year ~ courseId <>( {
      (name, year, courseId) => Group(None, name, year, courseId)
    }, {
      u: Group => Some((u.name, u.year, u.course))
    })

    def course = foreignKey("course_fk", courseId, Courses)(_.id)
  }

  class GroupMembers extends Table[(GroupId, CourseId)]("group-members") {
    def groupId = column[GroupId]("group_id")
    def courseId = column[CourseId]("course_id")

    def * = groupId ~ courseId
    def pk = primaryKey("group-members-pkey", (groupId, courseId))

    def group = foreignKey("group_fk", groupId, Groups)(_.id)
    def course = foreignKey("course_fk", courseId, Courses)(_.id)
  }

  //////////////////////////////////

  class Events extends Table[(EventId, String, String)]("event") {
    def id = column[EventId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[String]("description")

    def * = id ~ name ~ description
  }

  class Venues extends Table[(VenueId, String, String)]("venue") {
    def id = column[VenueId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[String]("description")

    def * = id ~ name ~ description
  }

  class Shows extends Table[(ShowId, EventId, VenueId, DateTime)]("show") {
    def id = column[ShowId]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[EventId]("event_id")
    def venueId = column[VenueId]("venue_id")
    def date = column[DateTime]("date")

    def * = id ~ eventId ~ venueId ~ date

    def event = foreignKey("event_fk", eventId, Events)(_.id)
    def venue = foreignKey("venue_fk", venueId, Venues)(_.id)
  }

  class Dvds extends Table[(DvdId, EventId, String, Int, DateTime, Option[DateTime], Boolean)]("dvd") {
    def id = column[DvdId]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[EventId]("event_id")
    def name = column[String]("name")
    def price = column[Int]("price")
    def availableStart = column[DateTime]("available-start")
    def availableEnd = column[Option[DateTime]]("available-end")
    def active = column[Boolean]("active")

    def * = id ~ eventId ~ name ~ price ~ availableStart ~ availableEnd ~ active

    def event = foreignKey("event_fk", eventId, Events)(_.id)
  }

  class EventParticipants extends Table[(EventId, MemberId, Int)]("event-participants") {
    def eventId = column[EventId]("event_id")
    def memberId = column[MemberId]("member_id")
    def allowedTicketReservation = column[Int]("allowed-ticket-reservations")

    def * = eventId ~ memberId ~ allowedTicketReservation

    def pk = primaryKey("event-participants_pkey", (eventId, memberId))

    def event = foreignKey("event_fk", eventId, Events)(_.id)
    def member = foreignKey("member_fk", memberId, Members)(_.id)
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
    def billingName = column[String]("billing-name")
    def billingAddress = column[String]("billing-address")

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

  class TicketSeatOrders extends Table[(TicketOrderId, ShowId, Option[MemberId])]("order-ticket-seat") {
    def ticketOrderId = column[TicketOrderId]("ticket_order_id")
    def showId = column[ShowId]("show_id")
    def memberId = column[Option[MemberId]]("member_id")

    def * = ticketOrderId ~ showId ~ memberId

    def pk = primaryKey("order-ticket-seat_pkey", (ticketOrderId, showId))

    def ticketOrder = foreignKey("ticket_order_fk", ticketOrderId, TicketOrders)(_.id)
    def show = foreignKey("show_fk", showId, Shows)(_.id)
    def member = foreignKey("member_fk", memberId, Members)(_.id)
  }


  implicit val userIdType = MappedTypeMapper.base[UserId, Long](_.id, new UserId(_))
  implicit val guestIdType = MappedTypeMapper.base[GuestId, Long](_.id, new GuestId(_))
  implicit val adminIdType = MappedTypeMapper.base[AdminId, Long](_.id, new AdminId(_))
  implicit val memberIdType = MappedTypeMapper.base[MemberId, Long](_.id, new MemberId(_))
  implicit val eventIdType = MappedTypeMapper.base[EventId, Long](_.id, new EventId(_))
  implicit val venueIdType = MappedTypeMapper.base[VenueId, Long](_.id, new VenueId(_))
  implicit val showIdType = MappedTypeMapper.base[ShowId, Long](_.id, new ShowId(_))
  implicit val dvdIdType = MappedTypeMapper.base[DvdId, Long](_.id, new DvdId(_))
  implicit val orderIdType = MappedTypeMapper.base[OrderId, Long](_.id, new OrderId(_))
  implicit val ticketOrderIdType = MappedTypeMapper.base[TicketOrderId, Long](_.id, new TicketOrderId(_))
  implicit val courseIdType = MappedTypeMapper.base[CourseId, Long](_.id, new CourseId(_))
  implicit val groupIdType = MappedTypeMapper.base[GroupId, Long](_.id, new GroupId(_))
}
