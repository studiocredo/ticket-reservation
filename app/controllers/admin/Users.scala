package controllers.admin

import be.studiocredo.{UserContextSupport, NotificationService, UserService}
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

import models.admin._

class Users @Inject()(val userService: UserService, val authService: AuthenticatorService, val notificationService: NotificationService) extends AdminController with UserContextSupport {
  val ListPage = Redirect(routes.Users.list())

  val userForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "username" -> nonEmptyText,
      "email" -> optional(email),
      "address" -> optional(text),
      "phone" -> optional(text)
    )(UserFormData.apply)(UserFormData.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val list = userService.page(page)
    Ok(views.html.admin.users(list, userContext))
  }

  def create() = AuthDBAction { implicit request =>
    Ok(views.html.admin.usersCreateForm(userForm, userContext))
  }
  def save() = AuthDBAction { implicit rs =>
    userForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.usersCreateForm(formWithErrors, userContext)),
      user => {
        userService.insert(user)

        ListPage.flashing("success" -> "User has been created")
      }
    )
  }

  def edit(id: UserId) = AuthDBAction { implicit rs =>
    userService.getEdit(id) match {
      case None => ListPage
      case Some(user) => Ok(views.html.admin.usersEditForm(id, userForm.fillAndValidate(user), userContext))
    }
  }
  def update(id: UserId) = AuthDBAction { implicit rs =>
    userForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.usersEditForm(id, formWithErrors, userContext)),
      user => {
        userService.update(id, user)

        ListPage.flashing("success" -> "User has been updated")
      }
    )
  }
}
