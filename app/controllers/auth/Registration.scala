package controllers.auth


import play.api.mvc._
import com.google.inject.Inject
import be.studiocredo.auth._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import be.studiocredo.UserService
import scala.Some
import be.studiocredo.auth.EmailToken
import play.api.mvc.SimpleResult
import play.api.mvc.Call
import play.api.data.validation.{Constraint, ValidationError, Valid, Invalid}
import models.entities.{UserDetailEdit, UserDetail, UserEdit}
import be.studiocredo.util.DBSupport

class Registration @Inject()(userService: UserService, val authService: AuthenticatorService) extends Controller with SecureUtils with Secure {
  val defaultAuthorization = None

  val startForm = Form(
    "email" -> email.verifying(nonEmpty)
  )


  def startSignUp = AuthAwareAction { implicit request =>
    withReferrerAsOriginalUrl(Ok(views.html.auth.startSignUp(startForm)))
  }

  def signUpAwaitEmail = AuthAwareAction { implicit request =>
    Ok(views.html.auth.signUpAwaitEmail())
  }

  def handleStartSignUp = AuthAwareDBAction { implicit request =>
    startForm.bindFromRequest.fold({
      errors => BadRequest(views.html.auth.startSignUp(errors))
    }, {
      email =>
        if (userService.findByEmail(email).isEmpty) {
          doSignUp(email)
        } else {
          Ok(views.html.auth.signUpExistingAccounts(startForm.fill(email), email))
        }
    })
  }

  def forceHandleStartSignUp(email: String) = AuthAwareAction { implicit request =>
    doSignUp(email)
  }

  private def doSignUp(email: String)(implicit request: SecureRequest[_]): SimpleResult = {
    val token = authService.createEmailToken(email)

    Mailer.sendSignUpEmail(email, token.id)
    Redirect(routes.Registration.signUpAwaitEmail())
  }

  // ----------------
  // ----------------

  val validPassword: Constraint[String] = Constraint({
    plainText =>
      if (plainText.length < 8)
        Invalid("Password must be at least 8 chars long")
      else
        Valid
  })

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

  def signUp(token: String) = AuthAwareAction { implicit req =>
    executeForToken(token, routes.Registration.startSignUp(), token => {
      Ok(views.html.auth.signUp(registerForm, token.id))
    })
  }

  def handleSignUp(token: String) = AuthAwareDBAction { implicit req => {
    executeForToken(token, routes.Registration.startSignUp(), token => {
      registerForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.signUp(errors, token.id))
      }, {
        info => {
          val userId = userService.insert(
            UserEdit(info.name, info.username, Passwords.hash(info.password.newPassword)),
            UserDetailEdit(Some(token.email), None, None)
          )
          // Mailer.sendWelcomeEmail(
          Redirect(routes.LoginPage.login()).flashing("success" -> "Thank you for signing up.  You can log in now")
        }
      })
    })
  }}


  // ----------------
  // ----------------

  def startResetPassword() = AuthAwareAction { implicit request =>
    NotImplemented
  }

  def handleStartResetPassword() = AuthAwareAction { implicit request =>
    NotImplemented
  }


  def resetPassword(token: String) = AuthAwareAction { implicit request =>
    NotImplemented
  }

  def handleResetPassword(token: String) = AuthAwareAction { implicit request =>
    NotImplemented
  }

  private def executeForToken(token: String, error: Call, f: EmailToken => SimpleResult)(implicit request: SecureRequest[_]): SimpleResult = {
    authService.checkEmailToken(token).fold(
      Redirect(error).flashing("error" -> "The link you followed no longer valid")
    )(
      f
    )
  }

}
