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
      )(PasswordSet.apply)(PasswordSet.unapply).verifying("Passwords do not match", passwords => passwords.newPassword == passwords.confirmation)
    )(ChangeInfo.apply)(ChangeInfo.unapply)
  )

  def page = AuthDBAction { implicit rq =>
    Ok(views.html.auth.pwChange(changePasswordForm, userContext))
  }

  def handlePasswordChange = AuthDBAction { implicit rq =>
    changePasswordForm.bindFromRequest.fold(errors => {
      BadRequest(views.html.auth.pwChange(errors, userContext))
    }, {
      info => {
        if (userService.changePassword(rq.user.id, Passwords.hash(info.newPassword.newPassword))) {
          userService.find(rq.user.id) map { user =>
            user.email map { email =>
              Mailer.sendPasswordChangedNotification(email, user)
            }
          }
          Redirect(routes.LoginPage.login()).flashing("success" -> "Password has been changed")
        } else {
          BadRequest(views.html.auth.pwChange(changePasswordForm.fill(info).withGlobalError("Failed to change password (internal error)"), userContext))
        }
      }
    })
  }
}
