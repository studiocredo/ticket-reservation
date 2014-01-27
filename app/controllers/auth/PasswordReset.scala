package controllers.auth

import play.api.mvc._
import com.google.inject.Inject
import be.studiocredo.auth._
import play.api.data.Form
import play.api.data.Forms._
import be.studiocredo.{NotificationSupport, NotificationService, UserService}
import be.studiocredo.util.DBSupport._
import be.studiocredo.auth.Passwords._
import scala.Some


class PasswordReset @Inject()(userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends Controller with SecureUtils with Secure with AuthUtils with NotificationSupport {
  val defaultAuthorization = None

  val startForm = Form(
    "email" -> email.verifying("No account found with this email address", email => {
      DB.withSession { implicit session =>
        !userService.findByEmail(email).isEmpty
      }
    })
  )

  def startResetPassword() = AuthAwareDBAction { implicit request =>
    Ok(views.html.auth.pwResetStart(startForm, notifications))
  }

  def handleStartResetPassword() = AuthAwareDBAction { implicit request =>
    startForm.bindFromRequest.fold({
      errors => BadRequest(views.html.auth.pwResetStart(errors, notifications))
    }, {
      email => {
        val users = userService.findByEmail(email)
        if (users.isEmpty) {
          BadRequest(views.html.auth.pwResetStart(startForm, notifications))
        } else {
          val token = authService.createEmailToken(email)
          Mailer.sendPasswordResetEmail(token.email, users, token)
          Ok(views.html.auth.pwResetEmail(notifications))
        }
      }
    })
  }

  val changePasswordForm = Form(
    mapping(
      "password" -> nonEmptyText.verifying(validPassword),
      "confirmation" -> nonEmptyText
    )(PasswordSet.apply)(PasswordSet.unapply).verifying("Passwords do not match", passwords => passwords.newPassword == passwords.confirmation)
  )

  def resetPassword(token: String, user: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startResetPassword(), token => {
      Ok(views.html.auth.pwResetForm(changePasswordForm, token.id, user, notifications))
    })
  }

  def handleResetPassword(token: String, username: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startResetPassword(), token => {
      changePasswordForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.pwResetForm(errors, token.id, username, notifications))
      }, {
        info => {
          if (userService.changePassword(token.email, username, Passwords.hash(info.newPassword))) {
            userService.find(token.email, username) map (user => Mailer.sendPasswordChangedNotification(token.email, user))

            Redirect(routes.LoginPage.login()).flashing("success" -> "Password has been changed")
          } else {
            BadRequest(views.html.auth.pwResetForm(changePasswordForm.fill(info).withGlobalError("Failed to reset password (internal error)"), token.id, username, notifications))
          }
        }
      })
    })
  }

}
