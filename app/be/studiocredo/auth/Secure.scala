/**
 * Copyright 2012 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
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

import play.api.mvc._


import play.api.libs.json.Json
import play.api.http.HeaderNames
import scala.Some
import play.api.mvc.SimpleResult
import scala.concurrent.Future
import be.studiocredo.util.DBSupport._
import be.studiocredo.util.DBImplicits
import play.Logger
import play.api.data.{FormError, Form}
import models.entities.{Identity, User}


sealed abstract class SecureRequest[A](request: Request[A], val currentUser: Option[Identity]) extends WrappedRequest(request)

case class SecuredRequest[A](user: Identity, request: Request[A]) extends SecureRequest(request, Some(user))
case class SecuredDBRequest[A](user: Identity, request: Request[A], dbSession: DBSession) extends SecureRequest(request, Some(user)) with HasDBSession

case class SecureAwareRequest[A](user: Option[Identity], request: Request[A]) extends SecureRequest(request, user)
case class SecureAwareDBRequest[A](user: Option[Identity], request: Request[A], dbSession: DBSession) extends SecureRequest(request, user) with HasDBSession


trait Secure extends Controller with SecureUtils with DBImplicits {
  val authService: AuthenticatorService

  val defaultAuthorization: Option[Authorization]

  // is this even remotely sane? i have no clue

  private[Secure] trait BaseAuth {
    type AB

    def apply[A](ajaxCall: Boolean): AB = apply(ajaxCall, defaultAuthorization)
    def apply[A](authorize: Authorization): AB = apply(false, Some(authorize))
    def apply[A](ajaxCall: Boolean, authorize: Authorization): AB = apply(ajaxCall, Some(authorize))

    def apply[A](ajaxCall: Boolean, authorize: Option[Authorization]): AB
  }


  object AuthAction extends AuthActionBuilder with BaseAuth {
    type AB = AuthActionBuilder

    override  def apply[A](ajaxCall: Boolean, authorize: Option[Authorization]): AB = new AuthActionBuilder(ajaxCall, authorize)
  }

  object AuthDBAction extends AuthDBActionBuilder with BaseAuth {
    type AB = AuthDBActionBuilder

    override def apply[A](ajaxCall: Boolean, authorize: Option[Authorization]): AB = new AuthDBActionBuilder(ajaxCall, authorize)
  }

  object AuthAwareAction extends AuthAwareActionBuilder with BaseAuth {
    type AB = AuthAwareActionBuilder

    override  def apply[A](ajaxCall: Boolean, authorize: Option[Authorization]): AB = new AuthAwareActionBuilder(ajaxCall, authorize)
  }

  object AuthAwareDBAction extends AuthAwareDBActionBuilder with BaseAuth {
    type AB = AuthAwareDBActionBuilder

    override  def apply[A](ajaxCall: Boolean, authorize: Option[Authorization]): AB = new AuthAwareDBActionBuilder(ajaxCall, authorize)
  }


  private def notAuthenticated[A](ajaxCall: Boolean, request: Request[A]): SimpleResult = {
    val response = if (ajaxCall) {
      ajaxCallNotAuthenticated(request)
    } else {
      Redirect(controllers.auth.routes.LoginPage.login())
        .flashing("error" -> "Je hebt geen toegang tot deze pagina")
        .withSession(request.session + (OriginalUrlKey -> request.uri)
      )
    }
    authService.signOutQuick(response)
  }

  private def ajaxCallNotAuthenticated[A](implicit request: Request[A]): SimpleResult = {
    Unauthorized(Json.toJson(Map("error" -> "Gelieve aan te melden"))).as(JSON)
  }


  class AuthDBActionBuilder(ajaxCall: Boolean = false, authorize: Option[Authorization] = None) extends ActionBuilder[({ type R[A] = SecuredDBRequest[A] })#R]() {
    protected def invokeBlock[A](request: Request[A], block: (SecuredDBRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      implicit val req = request
      DB.withSession { session: DBSession =>
        val user = authService.authenticate(authorize)

        user.fold({
          Future.successful(notAuthenticated(ajaxCall, request))
        })({
          user => block(SecuredDBRequest(user, request, session))
        })
      }
    }
  }

  class AuthActionBuilder(ajaxCall: Boolean= false, authorize: Option[Authorization] = defaultAuthorization) extends ActionBuilder[({ type R[A] = SecuredRequest[A] })#R] {
    protected def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      implicit val req = request
      val user = authService.authenticate(authorize)
      user.fold({
        Future.successful(notAuthenticated(ajaxCall, request))
      })({
        user => block(SecuredRequest(user, request))
      })
    }
  }

  class AuthAwareActionBuilder(ajaxCall: Boolean= false, authorize: Option[Authorization] = defaultAuthorization) extends ActionBuilder[({ type R[A] = SecureAwareRequest[A] })#R] {
    protected def invokeBlock[A](request: Request[A], block: (SecureAwareRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      implicit val req = request

      block(SecureAwareRequest(authService.authenticate(authorize), request))
    }
  }

  class AuthAwareDBActionBuilder(ajaxCall: Boolean= false, authorize: Option[Authorization] = defaultAuthorization) extends ActionBuilder[({ type R[A] = SecureAwareDBRequest[A] })#R] {
    protected def invokeBlock[A](request: Request[A], block: (SecureAwareDBRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      implicit val req = request
      DB.withSession { session: DBSession =>
        block(SecureAwareDBRequest(authService.authenticate(authorize), request, session))
      }
    }
  }
}

trait SecureUtils {
  val OriginalUrlKey = "original-url"
  def withReferrerAsOriginalUrl[A](result: SimpleResult)(implicit request: Request[A]): SimpleResult = {
    request.session.get(OriginalUrlKey) match {
      // If there's already an original url recorded we keep it: e.g. if s.o. goes to
      // login, switches to signup and goes back to login we want to keep the first referer
      case Some(_) => result
      case None => {
        request.headers.get(HeaderNames.REFERER).map { referer =>
        // we don't want to use the full referrer, as then we might redirect from https
        // back to http and loose our session. So let's get the path and query string only
          val idxFirstSlash = referer.indexOf("/", "https://".length())
          val refererUri = if (idxFirstSlash < 0) "/" else referer.substring(idxFirstSlash)
          result.withSession(
            request.session + (OriginalUrlKey -> refererUri))
        }.getOrElse(result)
      }
    }
  }
}
