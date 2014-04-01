/**
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package be.studiocredo.auth

import java.security.SecureRandom
import org.joda.time.{Minutes, DateTime}
import play.api.libs.Codecs
import play.api.Logger
import play.api.mvc._
import org.mindrot.jbcrypt.BCrypt
import com.google.inject.Inject
import scala._
import java.util.UUID
import scala.Some
import play.api.mvc.SimpleResult
import play.api.mvc.DiscardingCookie
import play.api.mvc.Cookie
import play.api.data.validation.{Valid, Invalid, Constraint}
import models.entities.{Identity, User}
import models.ids.UserId

class AuthenticatorService @Inject()(store: AuthTokenStore, identityService: IdentityService) {
  val cookieName = "id"
  val cookiePath = "/"
  val cookieDomain = None
  val cookieSecure = false
  val cookieHttpOnly = true
  val idleTimeout = Minutes.minutes(30)
  val absoluteTimeout = Minutes.minutes(12 * 60)

  def signIn(credentials: Credentials, success: => SimpleResult, failure: SignInError => SimpleResult)(implicit request: Request[_]): SimpleResult = {
    val identity = identityService.findByUserName(credentials.user)
    identity.fold(
      failure(TryAgain("Database error"))
    )(
      user => {
        if (Passwords.matches(user.user.password, credentials.password)) {
          success.withCookies(authCookie(create(user)))
          //failure(TryAgain("Error creating authenticator"))
        } else
          failure(InvalidCredentials)
      }
    )
  }

  def sudo(userId: UserId, success: => SimpleResult, failure: => SimpleResult)(implicit request: Request[_]): SimpleResult = {
    identityService.find(userId).fold(failure)(user => success.withCookies(authCookie(create(user))))
  }

  private def authCookie(authenticator: AuthToken): Cookie = {
    Cookie(
      cookieName,
      authenticator.id,
      Some(absoluteTimeout.toStandardSeconds.getSeconds),
      cookiePath,
      cookieDomain,
      secure = cookieSecure,
      httpOnly = cookieHttpOnly
    )
  }

  private val discardingCookie: DiscardingCookie = DiscardingCookie(cookieName, cookiePath, cookieDomain, cookieSecure)
  def signOut(result: SimpleResult)(implicit request: RequestHeader): SimpleResult = {
    authenticatorFromRequest.foreach(token => {
      if (Logger.isDebugEnabled) {
        val user = identityService.find(token.userId)
        Logger.debug(s"User $user logged out")
      }

      store.delete(token.id)
    })

    result.discardingCookies(discardingCookie)
  }
  def signOutQuick(result: SimpleResult) = result.discardingCookies(discardingCookie)

  def authenticate(authorize: Option[Authorization] = None)(implicit request: Request[_]): Option[Identity] = {
    for (
      authenticator <- authenticatorFromRequest;
      user <- identityService.find(authenticator.userId) if authorize.isEmpty || authorize.get.isAuthorized(user)
    ) yield {
      touch(authenticator)
      user
    }
  }

  def authenticatorFromRequest(implicit request: RequestHeader): Option[AuthToken] = {
    for {
      cookie <- request.cookies.get(cookieName)
      token <- store.findAuthToken(cookie.value) if isValid(token)
    } yield token
  }

  private def isValid(authenticator: Token): Boolean = {
    !authenticator.expirationDate.isBeforeNow && !authenticator.lastUsed.plus(idleTimeout).isBeforeNow
  }

  private def touch(authenticator: AuthToken) = store.save(authenticator.copy(lastUsed = DateTime.now()))
  private def touch(authenticator: EmailToken) = store.save(authenticator.copy(lastUsed = DateTime.now()))



  def checkEmailToken(tokenId: String): Option[EmailToken] = {
    for {
      token <- store.findEmailToken(tokenId) if isValid(token)
    } yield {
      touch(token)
      token
    }
  }

  def createEmailToken(email: String): EmailToken = {
    val id = IdGenerator.generate
    val now = DateTime.now()
    val expirationDate = now.plus(absoluteTimeout)
    val token = EmailToken(id, email, None, now, now, expirationDate)
    store.save(token)
    token
  }

  private def create(user: Identity): AuthToken = {
    val id = IdGenerator.generate
    val now = DateTime.now()
    val expirationDate = now.plus(absoluteTimeout)
    val token = AuthToken(id, user.id, now, now, expirationDate)
    store.save(token)
    token
  }
}

case class Credentials(user: String, password: String) {
  def userOnly = this.copy(password = "")
}

case class Password(hashed: String, salt: String)

object Passwords {
  val validPassword: Constraint[String] = Constraint({
    plainText =>
      if (plainText.length < 8)
        Invalid("Wachtwoord moet minstens 8 karakters bevatten")
      else
        Valid
  })

  def hash(plainPassword: String): Password = {
    val salt = BCrypt.gensalt(13)
    Password(BCrypt.hashpw(plainPassword, salt), salt)
  }

  def matches(passwordInfo: Password, suppliedPassword: String): Boolean = {
    BCrypt.checkpw(suppliedPassword, passwordInfo.hashed)
  }

  def matches(passwordInfo: Password, credentials: Credentials): Boolean = {
    matches(passwordInfo, credentials.password)
  }

  def random(): Password = hash(IdGenerator.generate(16))
}

sealed trait SignInError
case object InvalidCredentials extends SignInError
case class TryAgain(msg: String) extends SignInError


object IdGenerator {

  val random = new SecureRandom()

  def generate: String = generate(96)

  def generate(size: Int): String = {
    val randomValue = new Array[Byte](size)
    random.nextBytes(randomValue)
    Codecs.toHexString(randomValue)
  }
}

