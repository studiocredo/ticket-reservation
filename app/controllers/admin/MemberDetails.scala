package controllers.admin

import play.api.db.slick._
import be.studiocredo.{MemberService, CourseService, GroupsService}
import play.api.Play.current
import models.ids._
import play.api._
import play.api.mvc._
import models.entities.{Group, Member}
import be.studiocredo.util.Select2
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService


class MemberDetails @Inject()(groupService: GroupsService, courseService: CourseService, memberService: MemberService, val authService: AuthenticatorService) extends AdminController {
  val logger = Logger("group-details")



  def view(id: MemberId) = AuthDBAction { implicit rs =>
    memberService.memberDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for member $id")
      case Some(details) => Ok(views.html.admin.member(details))
    }
  }

  def addGroups(memberId: MemberId) = AuthDBAction(parse.urlFormEncoded) { implicit rs =>
    val groups = rs.request.body.get("class")
    Logger.debug(s"add member $memberId to group $groups")
    groups.foreach(ids => ids.map(id => GroupId(id.toInt)).foreach (id => groupService.addMembers(id, memberId)))

    Redirect(routes.MemberDetails.view(memberId)).flashing("success" -> "Added to class")
  }

  def ajaxGroups(id: MemberId) = AuthDBAction(ajaxCall = true) { implicit rs =>
    Select2.parse(rs).map {
      query => {
        val result = groupService.page(query.page, query.limit, filter = Some(query.query + '%'))

        Ok(Select2.respond(result, (g: Group) => g.id.toString, (g: Group) => s"(${g.year}) ${g.name}"))
      }
    }.getOrElse(BadRequest("Missing parameters"))
  }
}
