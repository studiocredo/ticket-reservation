package controllers.auth

import play.api.mvc._
import com.google.inject.Inject
import be.studiocredo.auth._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import be.studiocredo.{NotificationSupport, NotificationService, UserService}
import scala.Some
import play.api.mvc.SimpleResult
import play.api.data.validation.{Constraint, Valid, Invalid}
import models.entities.{UserDetailEdit, UserEdit}
import be.studiocredo.util.DBSupport

case class PasswordSet(newPassword: String, confirmation: String)
case class RegistrationInfo(name:String, username: String, password: PasswordSet)

class SignUp @Inject()(userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends Controller with SecureUtils with Secure with AuthUtils with NotificationSupport {
  val defaultAuthorization = None

  val startForm = Form(
    "email" -> email.verifying(nonEmpty)
  )


  def startSignUp = AuthAwareDBAction { implicit request =>
    withReferrerAsOriginalUrl(Ok(views.html.auth.signUpStart(startForm, notifications)))
  }

  def signUpAwaitEmail = AuthAwareDBAction { implicit request =>
    Ok(views.html.auth.signUpEmail(notifications))
  }

  def handleStartSignUp = AuthAwareDBAction { implicit request =>
    startForm.bindFromRequest.fold({
      errors => BadRequest(views.html.auth.signUpStart(errors, notifications))
    }, {
      email =>
        if (userService.findByEmail(email).isEmpty) {
          doSignUp(email)
        } else {
          Ok(views.html.auth.signUpExistingAccounts(startForm.fill(email), email, notifications))
        }
    })
  }

  def forceHandleStartSignUp(email: String) = AuthAwareAction { implicit request =>
    doSignUp(email)
  }

  private def doSignUp(email: String)(implicit request: SecureRequest[_]): SimpleResult = {
    val token = authService.createEmailToken(email)

    Mailer.sendSignUpEmail(email, token)
    Redirect(routes.SignUp.signUpAwaitEmail())
  }

  // ----------------
  // ----------------

  import Passwords._

  val validUsername: Constraint[String] = Constraint({
    username =>
      DBSupport.DB.withSession {
        implicit session =>
          if (userService.findByUserName(username).isDefined)
            Invalid("Username already taken")
          else
            Valid
      }
  })


  val registerForm = Form[RegistrationInfo](
    mapping(
      "name" -> nonEmptyText,
      "username" -> nonEmptyText.verifying(validUsername),
      "password" ->
        mapping(
          "password" -> nonEmptyText.verifying(validPassword),
          "confirmation" -> nonEmptyText
        )(PasswordSet.apply)(PasswordSet.unapply).verifying("Passwords do not match", passwords => passwords.newPassword == passwords.confirmation)
    )
      ((name, username, password) => RegistrationInfo(name, username, password))
      (info => Some((info.name, info.username, info.password)))
  )

  def signUp(token: String) = AuthAwareDBAction { implicit req =>
    executeForToken(token, routes.SignUp.startSignUp(), token => {
      Ok(views.html.auth.signUpForm(registerForm, token.id, notifications))
    })
  }

  def handleSignUp(token: String) = AuthAwareDBAction { implicit req => {
    executeForToken(token, routes.SignUp.startSignUp(), token => {
      registerForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.signUpForm(errors, token.id, notifications))
      }, {
        info => {
          val userId = userService.insert(
            UserEdit(info.name, info.username, Passwords.hash(info.password.newPassword)),
            UserDetailEdit(Some(token.email), None, None)
          )
          Redirect(routes.LoginPage.login()).flashing("success" -> "Thank you for signing up. You can log in now")
        }
      })
    })
  }}
}
