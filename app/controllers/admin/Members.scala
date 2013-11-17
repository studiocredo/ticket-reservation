package controllers.admin

import be.studiocredo.MemberService
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities._
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

import models.admin._

class Members @Inject()(memberService: MemberService, val authService: AuthenticatorService) extends AdminController {
  val ListPage = Redirect(routes.Members.list())

  val memberForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "username" -> nonEmptyText,
      "email" -> optional(email),
      "address" -> optional(text),
      "phone" -> optional(text)
    )(MemberFormData.apply)(MemberFormData.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val list = memberService.page(page)
    Ok(views.html.admin.members(list))
  }

  def create() = AuthAction { implicit request =>
    Ok(views.html.admin.membersCreateForm(memberForm))
  }
  def save() = AuthDBAction { implicit rs =>
    memberForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.membersCreateForm(formWithErrors)),
      member => {
        memberService.insert(member)

        ListPage.flashing("success" -> "Member has been created")
      }
    )
  }

  def edit(id: MemberId) = AuthDBAction { implicit rs =>
    memberService.getEdit(id) match {
      case None => ListPage
      case Some(member) => Ok(views.html.admin.membersEditForm(id, memberForm.fillAndValidate(member)))
    }
  }
  def update(id: MemberId) = AuthDBAction { implicit rs =>
    memberForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.membersEditForm(id, formWithErrors)),
      member => {
        memberService.update(id, member)

        ListPage.flashing("success" -> "Member has been updated")
      }
    )
  }
  def delete(id: MemberId) = AuthDBAction { implicit rs =>
    memberService.delete(id)

    ListPage.flashing("success" -> "Member has been deleted")
  }
}
