package models

/* Tables already converted to queries, to reduce boilerplate an avoid confusion when conversions happens implicitly and when Query(_) must be applied explicitly. */
object tables {

  import play.api.db.slick.Config.driver.simple._

  val Users = Query(schema.tables.Users)
  val Guests = Query(schema.tables.Guests)
  val Members = Query(schema.tables.Members)
  val Admins = Query(schema.tables.Admins)
  val Courses = Query(schema.tables.Courses)
  val Groups = Query(schema.tables.Groups)
  val GroupMembers = Query(schema.tables.GroupMembers)
  val Events = Query(schema.tables.Events)
  val Venues = Query(schema.tables.Venues)
  val Shows = Query(schema.tables.Shows)
  val Dvds = Query(schema.tables.Dvds)
  val EventParticipants = Query(schema.tables.EventParticipants)
  val TicketReservations = Query(schema.tables.TicketReservations)
  val Orders = Query(schema.tables.Orders)
  val TicketOrders = Query(schema.tables.TicketOrders)
  val TicketSeatOrders = Query(schema.tables.TicketSeatOrders)

}
