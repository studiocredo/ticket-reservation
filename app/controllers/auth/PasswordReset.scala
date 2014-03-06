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

  val HomePage = Redirect(controllers.routes.Application.index())

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

  def startActivateProfile(userName: String) = AuthAwareDBAction { implicit request =>
    userService.findByUserName(userName) match {
      case None => HomePage
      case Some(user) => {
        if (user.active) HomePage else {
          Ok(views.html.auth.profileActivateStart(user, userContext))
        }
      }
    }
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

  def handleStartActivateProfile(userName: String) = AuthAwareDBAction { implicit request =>
    userService.findByUserName(userName) match {
      case None => HomePage
      case Some(user) => {
        if (user.active) HomePage else {
          user.email match {
            case None => HomePage
            case Some(email) => {
              val token = authService.createEmailToken(email)
              Mailer.sendProfileActivationEmail(token.email, user, token)
              HomePage.flashing("success" -> "Aanvraag tot activatie geregistreerd")
            }
          }
        }
      }
    }
  }

  val changePasswordForm = Form(
    mapping(
      "password" -> nonEmptyText.verifying(validPassword),
      "confirmation" -> nonEmptyText
    )(PasswordSet.apply)(PasswordSet.unapply).verifying("De wachtwoorden zijn verschillend", passwords => passwords.newPassword == passwords.confirmation)
  )

  def resetPassword(token: String, user: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startResetPassword(), token => {
      Ok(views.html.auth.pwResetForm(changePasswordForm, token.id, user, false, userContext))
    })
  }

  def handleResetPassword(token: String, username: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startResetPassword(), token => {
      changePasswordForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.pwResetForm(errors, token.id, username, false, userContext))
      }, {
        info => {
          if (userService.changePassword(token.email, username, Passwords.hash(info.newPassword))) {
            userService.find(token.email, username) map (user => Mailer.sendPasswordChangedNotification(token.email, user))

            Redirect(routes.LoginPage.login()).flashing("success" -> "Wachtwoord gewijzigd")
          } else {
            BadRequest(views.html.auth.pwResetForm(changePasswordForm.fill(info).withGlobalError("Er is een interne fout opgetreden. Het wachtwoord werd niet gewijzigd."), token.id, username, false, userContext))
          }
        }
      })
    })
  }

  def activateProfile(token: String, username: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startActivateProfile(username), token => {
      Ok(views.html.auth.pwResetForm(changePasswordForm, token.id, username, true, userContext))
    })
  }

  def handleActivateProfile(token: String, username: String) = AuthAwareDBAction { implicit request =>
    executeForToken(token, routes.PasswordReset.startActivateProfile(username), token => {
      changePasswordForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.pwResetForm(errors, token.id, username, true, userContext))
      }, {
        info => {
          if (userService.changePasswordAndActivate(token.email, username, Passwords.hash(info.newPassword))) {
            userService.find(token.email, username) map (user => Mailer.sendPasswordChangedNotification(token.email, user))
            Redirect(routes.LoginPage.login()).flashing("success" -> "Wachtwoord gewijzigd")
          } else {
            BadRequest(views.html.auth.pwResetForm(changePasswordForm.fill(info).withGlobalError("Er is een interne fout opgetreden. Het wachtwoord werd niet gewijzigd."), token.id, username, true, userContext))
          }
        }
      })
    })
  }

}
