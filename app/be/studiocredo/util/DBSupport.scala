package be.studiocredo.util

import play.api.mvc._

import play.api.Play.current
import play.api.db.slick.Database
import play.api.db.slick.SlickExecutionContext
import scala.concurrent.Future
import play.api.mvc.{AnyContent, Action, SimpleResult}


object DBSupport extends DBImplicits {

  type DBSession = scala.slick.session.Session

  val DB = Database()(current)

  trait HasDBSession {
    def dbSession: play.api.db.slick.Session
  }
  case class DBRequest[A](request: Request[A], dbSession: play.api.db.slick.Session) extends WrappedRequest[A](request) with HasDBSession

}

trait DBImplicits {

  import DBSupport._

  implicit def toDBSession[_](implicit r: HasDBSession): DBSession = r.dbSession
}

object DBAction {

  import DBSupport._

  val (executionContext, tp) = SlickExecutionContext.threadPoolExecutionContext(5, 20)

  def explicit(f: scala.slick.session.Session => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
    Action.async { implicit request =>
      Future {
        DB.withSession { session: DBSession =>
          f(session)(request)
        }
      }(executionContext)
    }
  }

  def apply(f: DBRequest[AnyContent] => SimpleResult): Action[AnyContent] = {
    Action.async { implicit request =>
      Future {
        DB.withSession { implicit session: DBSession =>
          f(DBRequest(request, session))
        }
      }(executionContext)
    }
  }
}

