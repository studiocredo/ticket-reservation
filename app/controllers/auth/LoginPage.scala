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
package controllers.auth

import play.api.mvc._
import be.studiocredo.auth._
import play.api.Logger
import be.studiocredo.UserService
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import play.api.mvc.SimpleResult
import com.google.inject.Inject

class LoginPage @Inject()(val authService: AuthenticatorService, userService: UserService) extends Controller with Secure {
  val defaultAuthorization = None

  def toUrl(implicit request: RequestHeader) = session.get(OriginalUrlKey).getOrElse("/")

  val loginForm = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )(Credentials.apply)(Credentials.unapply)
  )

  def login = AuthAwareAction { implicit request =>
    if (request.currentUser.isDefined) {
      Redirect("/")
    } else {
      withReferrerAsOriginalUrl(Ok(views.html.auth.login(loginForm, None)))
    }
  }

  def logout = AuthAwareAction { implicit request =>
    authService.signOut(Redirect(controllers.auth.routes.LoginPage.login()))
  }

  def handleLogin() = AuthAwareAction { implicit request =>
    loginForm.bindFromRequest().fold(
      errors => Ok(views.html.auth.login(errors, None)),
      credentials => {
        authService.signIn(credentials, Redirect(toUrl).withSession(session - OriginalUrlKey), error => handleLoginError(error, credentials))
      }
    )
  }

  def handleLoginError(error: SignInError, credentials: Credentials)(implicit request: SecureRequest[_]): SimpleResult = {
    error match {
      case InvalidCredentials => {
        BadRequest(views.html.auth.login(loginForm.fill(credentials.userOnly), Some("Invalid credentials")))
      }
      case TryAgain(msg) => {
        Redirect(controllers.auth.routes.LoginPage.login()).flashing("error" -> "An error occurred while logging you in. Please try again.")
      }
      case _ => {
        Redirect(controllers.auth.routes.LoginPage.login()).flashing("error" -> "An error occurred while logging you in. Please try again.")
      }
    }
  }

}
