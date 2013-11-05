package controllers.admin

import play.api.db.slick._
import be.studiocredo.{MemberService, CourseService, GroupsService}
import play.api.Play.current
import models.ids._
import play.api._
import play.api.mvc._
import models.entities.{Member, Group}
import be.studiocredo.util.Select2
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService


class GroupDetails @Inject()(groupService: GroupsService, courseService: CourseService, memberService: MemberService, val authService: AuthenticatorService) extends AdminController {
  val logger = Logger("group-details")

  def view(id: GroupId) = AuthDBAction { implicit rs =>
    groupService.groupDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for class $id")
      case Some(details) => Ok(views.html.admin.group(details))
    }
  }

  def addMembers(id: GroupId) = AuthDBAction(parse.urlFormEncoded) { implicit rs =>
    rs.request.body.get("member").foreach { ids =>
      groupService.addMembers(id, (ids map (id => MemberId(id.toLong))).toList)
    }

    Redirect(routes.GroupDetails.view(id)).flashing("success" -> "Member has been added")
  }

  def ajaxMembers(id: GroupId) = AuthDBAction(ajaxCall = true) { implicit rs =>
    Select2.parse(rs).map {
      query => {
        val result = memberService.page(query.page, query.limit, filter = Some(query.query + '%'))

        Ok(Select2.respond(result, (m: Member) => m.id.toString, (m: Member) => m.userId.toString)) // TODO name
      }
    }.getOrElse(BadRequest("Missing parameters"))
  }
}
