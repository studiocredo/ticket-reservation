package controllers.admin

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.MemberService
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities._
/*
class Members @Inject()(memberService: MemberService, val authService: AuthenticatorService) extends AdminController {
  val ListPage = Redirect(routes.Members.list())

  val memberForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> optional(email),
      "address" -> optional(text),
      "phone" -> optional(text),
      "arhived" -> boolean
    )(MemberEdit.apply)(MemberEdit.unapply)
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

        ListPage.flashing("success" -> "Member '%s' has been created".format(member.name))
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

        ListPage.flashing("success" -> "Member '%s' has been updated".format(member.name))
      }
    )
  }
  def delete(id: MemberId) = AuthDBAction { implicit rs =>
    memberService.delete(id)

    ListPage.flashing("success" -> "Member has been deleted")
  }
}
*/
