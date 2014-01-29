import java.io.File
import play.api._
import play.api.Play
 
val application = new DefaultApplication(new File("."), this.getClass.getClassLoader, None, Mode.Dev)
Play.start(application)

import play.api.Play.current

import be.studiocredo._
import be.studiocredo.auth._
import be.studiocredo.util._
import models.ids._
import models.admin._
import models.entities._
import play.api.db.slick._
import org.joda.time.DateTime

val vs = new VenueService()
val os = new OrderService()
val prs = new PreReservationService(os)
val ss = new ShowService(vs, os, prs)
val es = new EventService(ss)
val us = new UserService()
val is = new IdentityService(us)

implicit val session = DB.createSession()