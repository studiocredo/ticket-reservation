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
val os = new OrderService()
val ss = new ShowService(vs, os)
val es = new EventService(ss)
val us = new UserService()
val prs = new PreReservationService()

implicit val session = DB.createSession()