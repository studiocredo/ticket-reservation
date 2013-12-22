package controllers.admin

import be.studiocredo.UserService
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

import models.admin._

class Users @Inject()(userService: UserService, val authService: AuthenticatorService) extends AdminController {
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
    Ok(views.html.admin.users(list))
  }

  def create() = AuthAction { implicit request =>
    Ok(views.html.admin.usersCreateForm(userForm))
  }
  def save() = AuthDBAction { implicit rs =>
    userForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.usersCreateForm(formWithErrors)),
      user => {
        userService.insert(user)

        ListPage.flashing("success" -> "User has been created")
      }
    )
  }

  def edit(id: UserId) = AuthDBAction { implicit rs =>
    userService.getEdit(id) match {
      case None => ListPage
      case Some(user) => Ok(views.html.admin.usersEditForm(id, userForm.fillAndValidate(user)))
    }
  }
  def update(id: UserId) = AuthDBAction { implicit rs =>
    userForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.usersEditForm(id, formWithErrors)),
      user => {
        userService.update(id, user)

        ListPage.flashing("success" -> "User has been updated")
      }
    )
  }
}
