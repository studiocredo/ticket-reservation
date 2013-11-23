package controllers.admin

import be.studiocredo.{MemberService}
import play.api.Play.current
import models.ids._
import play.api._
import play.api.mvc._
import models.entities.{ Member}
import be.studiocredo.util.Select2
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService


class MemberDetails @Inject()(memberService: MemberService, val authService: AuthenticatorService) extends AdminController {
  val logger = Logger("group-details")


  def view(id: MemberId) = AuthDBAction { implicit rs =>
    memberService.get(id) match {
      case None => BadRequest(s"Failed to retrieve details for member $id")
      case Some(details) => Ok(views.html.admin.member(details))
    }
  }
}
