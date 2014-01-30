package controllers.auth

import play.api.mvc._
import com.google.inject.Inject
import be.studiocredo.auth._
import play.api.data.Form
import play.api.data.Forms._
import be.studiocredo.{UserContextSupport, NotificationService, UserService}
import be.studiocredo.util.DBSupport._
import be.studiocredo.auth.Passwords._
import scala.Some


class PasswordReset @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends Controller with SecureUtils with Secure with AuthUtils with UserContextSupport {
  val defaultAuthorization = None

  val startForm = Form(
    "email" -> email.verifying("Er bestaat geen gebruiker met email adres", email => {
      DB.withSession { implicit session =>
        !userService.findByEmail(email).isEmpty
      }
    })
  )

  def startResetPassword() = AuthAwareDBAction { implicit request =>
    Ok(views.html.auth.pwResetStart(startForm, userContext))
  }

  def handleStartResetPassword() = AuthAwareDBAction { implicit request =>
    startForm.bindFromRequest.fold({
      errors => BadRequest(views.html.auth.pwResetStart(errors, userContext))
    }, {
      email => {
        val users = userService.findByEmail(email)
        if (users.isEmpty) {
          BadRequest(views.html.auth.pwResetStart(startForm, userContext))
        } else {
          val token = authService.createEmailToken(email)
          Mailer.sendPasswordResetEmail(token.email, users, token)
          Ok(views.html.auth.pwResetEmail(userContext))
        }
      }
    })
  }

  val changePasswordForm = Form(
    mapping(
      "password" -> nonEmptyText.verifying(validPassword),
      "confirmation" -> nonEmptyText
    )(PasswordSet.apply)(PasswordSet.unapply).verifying("De wachtwoorden zijn verschillend", passwords => passwords.newPassword == passwords.confirmation)
  )

  def resetPassword(token: String, user: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startResetPassword(), token => {
      Ok(views.html.auth.pwResetForm(changePasswordForm, token.id, user, userContext))
    })
  }

  def handleResetPassword(token: String, username: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startResetPassword(), token => {
      changePasswordForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.pwResetForm(errors, token.id, username, userContext))
      }, {
        info => {
          if (userService.changePassword(token.email, username, Passwords.hash(info.newPassword))) {
            userService.find(token.email, username) map (user => Mailer.sendPasswordChangedNotification(token.email, user))

            Redirect(routes.LoginPage.login()).flashing("success" -> "Wachtwoord gewijzigd")
          } else {
            BadRequest(views.html.auth.pwResetForm(changePasswordForm.fill(info).withGlobalError("Er is een interne fout opgetreden. Het wachtwoord werd niet gewijzigd."), token.id, username, userContext))
          }
        }
      })
    })
  }

}
