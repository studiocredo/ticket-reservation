package controllers

import play.api.mvc._
import com.google.inject.Inject
import be.studiocredo.auth._
import be.studiocredo.{UserContextSupport, NotificationService, UserService}
import play.api.data.Form
import play.api.data.Forms._
import models.admin.UserFormData
import play.api.data.validation.{Valid, Invalid, Constraint}
import be.studiocredo.util.ServiceReturnValues._
import controllers.auth.Mailer

case class EmailSet(email: Option[String], confirmation: Option[String])

class Profile @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends Controller with Secure with UserContextSupport {
  val defaultAuthorization = Some(Authorization.ANY)

  val HomePage = Redirect(routes.Application.index())

  val validEmails: Constraint[EmailSet] = Constraint({
    emails => emails.email match {
      case None => {
        emails.confirmation match {
          case None => Valid
          case Some(_) => Invalid("Beide e-mail velden moeten ingevuld zijn")
        }
      }
      case Some(email) => {
        emails.confirmation match {
          case None => Invalid("Beide e-mail velden moeten ingevuld zijn")
          case Some(confirmation) => {
            if (email == confirmation) Valid else Invalid("E-mail velden zijn verschillend")
          }
        }
      }
    }

  })

  val profileForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "username" -> nonEmptyText,
      "email" -> mapping(
        "email" -> optional(email),
        "confirmation" -> optional(email)
      )(EmailSet.apply)(EmailSet.unapply).verifying(validEmails),
      "address" -> optional(text),
      "phone" -> optional(text)
    )((name, userName, emailSet, address, phone) => UserFormData(name,userName, emailSet.email, address, phone, true))(ufd => Some((ufd.name, ufd.username, EmailSet(ufd.email, None), ufd.address, ufd.phone)))
  )

  def edit = AuthDBAction { implicit rq =>
    rq.currentUser match {
      case None => HomePage
      case Some(user) => {
        userService.getEdit(user.id) match {
          case None => HomePage
          case Some(userFormData) => Ok(views.html.profileForm(profileForm.fillAndValidate(userFormData), userContext))
        }
      }
    }
  }

  def update = AuthDBAction { implicit rs =>
    rs.currentUser match {
      case None => HomePage
      case Some(user) => {
        val bindedForm = profileForm.bindFromRequest
        bindedForm.fold(
          formWithErrors => BadRequest(views.html.profileForm(formWithErrors, userContext)),
          userFormData => {
            userService.update(user.id, userFormData).fold(
              failure => BadRequest(views.html.profileForm(bindedForm.withGlobalError(serviceMessage(failure)), userContext)),
              success => {
                Mailer.sendProfileUpdatedEmail(user.user)
                HomePage.flashing("success" -> "Profiel aangepast")
              }
            )
          }
        )
      }
    }
  }
}
