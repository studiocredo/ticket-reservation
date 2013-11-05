package be.studiocredo.auth

import org.joda.time.{Duration, Interval, DateTime}
import models.ids.UserId
import play.api.cache.Cache
import play.api.Play.current
import com.google.inject.Inject
import be.studiocredo.Service
import akka.actor.Cancellable
import play.api.libs.concurrent.Akka


trait Token {
  def creationDate: DateTime
  def lastUsed: DateTime
  def expirationDate: DateTime
}
case class AuthToken(id: String, userId: UserId, creationDate: DateTime, lastUsed: DateTime, expirationDate: DateTime) extends Token
case class EmailToken(id: String, email: String, userId: Option[UserId], creationDate: DateTime, lastUsed: DateTime, expirationDate: DateTime) extends Token


trait AuthTokenStore {
  def save(token: AuthToken)
  def save(token: EmailToken)

  def findAuthToken(id: String): Option[AuthToken]
  def findEmailToken(id: String): Option[EmailToken]

  def delete(id: String)

  def deleteExpiredTokens()
}

class CacheAuthTokenStore extends AuthTokenStore {

  def expireSeconds(token: Token): Int = {
    Math.max(5, new Duration(DateTime.now(), token.expirationDate).getStandardSeconds.toInt)
  }

  override def save(token: AuthToken) = Cache.set(token.id, token, expireSeconds(token))
  override def save(token: EmailToken) = Cache.set(token.id, token, expireSeconds(token))

  override def findAuthToken(id: String): Option[AuthToken] = Cache.getAs[AuthToken](id)
  override def findEmailToken(id: String): Option[EmailToken] = Cache.getAs[EmailToken](id)

  override def delete(id: String) = Cache.remove(id)

  override def deleteExpiredTokens() = {
    // Cache handles it
  }
}


class DbAuthTokenStore extends AuthTokenStore {

  import play.api.db.slick.Config.driver.simple._
  import play.api.db.slick._
  import models.schema.tables._

  val AuthTokensQ = Query(AuthTokens)
  val EmailAuthTokensQ = Query(EmailAuthTokens)
  override def save(token: AuthToken) = {
    DB.withTransaction { implicit session: Session =>
      val byId = AuthTokens.filter(_.id === token.id)
      if (byId.exists.run) {
        byId.update(token)
      } else {
        AuthTokens.insert(token)
      }
    }
  }
  override def save(token: EmailToken) = {
    DB.withTransaction { implicit session: Session =>
      val byId = EmailAuthTokens.filter(_.id === token.id)
      if (byId.exists.run) {
        byId.update(token)
      } else {
        EmailAuthTokens.insert(token)
      }
    }
  }

  override def findAuthToken(id: String): Option[AuthToken] = {
    DB.withSession { implicit session: Session =>
      AuthTokensQ.filter(_.id === id).firstOption
    }
  }
  override def findEmailToken(id: String): Option[EmailToken] = {
    DB.withSession { implicit session: Session =>
      EmailAuthTokensQ.filter(_.id === id).firstOption
    }
  }

  override def delete(id: String) = {
    DB.withSession { implicit session: Session =>
      AuthTokensQ.filter(_.id === id).delete
      EmailAuthTokensQ.filter(_.id === id).delete
    }
  }

  override def deleteExpiredTokens() {
    DB.withSession { implicit session: Session =>
      import com.github.tototoshi.slick.JodaSupport._
      AuthTokensQ.filter(_.expiration < DateTime.now()).delete
      EmailAuthTokensQ.filter(_.expiration < DateTime.now()).delete
    }
  }
}


class AuthTokenExpireService @Inject()(authTokenStore: AuthTokenStore) extends Service {

  var cancellable: Option[Cancellable] = None

  override def onStop() {
    cancellable.map(_.cancel())
  }

  override def onStart() {
    import play.api.Play.current
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    cancellable = Some(
      Akka.system.scheduler.schedule(0.seconds, 5.minutes) {
        authTokenStore.deleteExpiredTokens()
      }
    )
  }
}
