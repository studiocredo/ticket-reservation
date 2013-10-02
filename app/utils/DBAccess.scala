package utils

import scala.slick.session.Database
import play.api.db.DB

trait DBAccess {
  import play.api.Play.current
  lazy val database = Database.forDataSource(DB.getDataSource())

  def withSession[T](f : scala.Function1[scala.slick.session.Session, T]) : T = database.withSession(f)
}
