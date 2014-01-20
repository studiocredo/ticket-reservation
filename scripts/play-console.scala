import java.io.File
import play.api._
import play.api.Play
 
val application = new DefaultApplication(new File("."), this.getClass.getClassLoader, None, Mode.Dev)
Play.start(application)

import play.api.Play.current

import be.studiocredo._
import models.ids._
import models.admin._
import models.entities._
import play.api.db.slick._

val vs = new VenueService()

val venue = DB.withSession { implicit session: scala.slick.session.Session =>
  vs.get(VenueId(1))
}

VenueCapacity(venue.get).total
VenueCapacity(venue.get).byType(SeatType.Normal)
VenueCapacity(venue.get).byType(SeatType.Vip)
VenueCapacity(venue.get).byType(SeatType.Disabled)
