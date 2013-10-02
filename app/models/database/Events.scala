package models.database

import play.api.db.slick.Config.driver.simple._

import com.github.tototoshi.slick.JodaSupport._
import org.joda.time.DateTime

class Event extends Table[(Long, String, String)]("event") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def description = column[String]("description")

  def * = id ~ name ~ description

}


class Venue extends Table[(Long, String, String, Int)]("venue") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def description = column[String]("description")
  def seats = column[Int]("seats")

  def * = id ~ name ~ description ~ seats
}

class Presentation extends Table[(Long, Long, Long, DateTime)]("presentation") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def eventId = column[Long]("event_id")
  def venueId = column[Long]("venue_id")
  def date = column[DateTime]("date")

  def * = id ~ eventId ~ venueId ~ date

  def event = foreignKey("event_fk", eventId, new Event)(_.id)
  def venue = foreignKey("venue_fk", venueId, new Venue)(_.id)
}
