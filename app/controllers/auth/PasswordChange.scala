package controllers.auth


import play.api.mvc._
import com.google.inject.Inject
import be.studiocredo.auth._
import be.studiocredo.{UserContextSupport, NotificationService, UserService}
import play.api.data.Form
import play.api.data.Forms._
import be.studiocredo.auth.Passwords._
import scala.Some

case class ChangeInfo(current: String, newPassword: PasswordSet)

class PasswordChange @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends Controller with Secure with UserContextSupport {
  val defaultAuthorization: Option[Authorization] = None

  val changePasswordForm = Form(
    mapping(
      "current" -> nonEmptyText,
      "password" -> mapping(
        "password" -> nonEmptyText.verifying(validPassword),
        "confirmation" -> nonEmptyText
      )(PasswordSet.apply)(PasswordSet.unapply).verifying("De wachtwoorden zijn verschillend", passwords => passwords.newPassword == passwords.confirmation)
    )(ChangeInfo.apply)(ChangeInfo.unapply)
  )

  def page = AuthDBAction { implicit rq =>
    Ok(views.html.auth.pwChange(rq.user.user, changePasswordForm, userContext))
  }

  def handlePasswordChange = AuthDBAction { implicit rq =>
    changePasswordForm.bindFromRequest.fold(errors => {
      BadRequest(views.html.auth.pwChange(rq.user.user, errors, userContext))
    }, {
      info => {
        if (userService.changePassword(rq.user.id, Passwords.hash(info.newPassword.newPassword))) {
          userService.find(rq.user.id) map { user =>
            user.email map { email =>
              Mailer.sendPasswordChangedNotification(email, user)
            }
          }
          Redirect(routes.LoginPage.login()).flashing("success" -> "Wachtwoord gewijzigd")
        } else {
          BadRequest(views.html.auth.pwChange(rq.user.user, changePasswordForm.fill(info).withGlobalError("Er is een interne fout opgetreden. Het wachtwoord werd niet gewijzigd."), userContext))
        }
      }
    })
  }
}
