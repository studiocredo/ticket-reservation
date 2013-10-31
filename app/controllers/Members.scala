package controllers

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.MemberService
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities.Member

object Members extends Controller {
  val memberService = new MemberService()

  val ListPage = Redirect(routes.Members.list())

  val memberForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> optional(email),
      "address" -> optional(text),
      "phone" -> optional(text)
    )
    ({ (name, email, address, phone) => Member(None, name, email, address, phone, active = true)})
    ({ member => Some(member.name, member.email, member.address, member.phone)})
  )

  def list(page: Int) = DBAction { implicit rs =>

    val list = memberService.page(page)
    Ok(views.html.members(list))

  }


  def create() = Action {
    Ok(views.html.membersCreateForm(memberForm))
  }
  def save() = DBAction { implicit rs =>
    memberForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.membersCreateForm(formWithErrors)),
      member => {
        memberService.insert(member)

        ListPage.flashing("success" -> "Member %s has been created".format(member.name))
      }
    )
  }

  def edit(id: MemberId) = DBAction { implicit rs =>
    memberService.get(id) match {
      case None => ListPage
      case Some(member) => Ok(views.html.membersEditForm(id, memberForm.fillAndValidate(member)))
    }
  }
  def update(id: MemberId) = DBAction { implicit rs =>
    memberForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.membersEditForm(id, formWithErrors)),
      member => {
        memberService.update(id, member)

        ListPage.flashing("success" -> "Member %s has been updated".format(member.name))
      }
    )
  }
  def delete(id: MemberId) = DBAction { implicit rs =>
    memberService.delete(id)

    ListPage.flashing("success" -> "Member has been deleted")
  }
}
