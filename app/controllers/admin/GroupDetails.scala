package controllers.admin

import play.api.db.slick._
import be.studiocredo.{MemberService, CourseService, GroupsService}
import play.api.Play.current
import models.ids._
import play.api._
import play.api.mvc._
import models.entities.{Member, Group}
import be.studiocredo.util.Select2


object GroupDetails extends Controller {
  val logger = Logger("group-details")
  val groupService = new GroupsService()
  val courseService = new CourseService()
  val memberService = new MemberService()


  def view(id: GroupId) = DBAction { implicit rs =>
    groupService.groupDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for class $id")
      case Some(details) => Ok(views.html.admin.group(details))
    }
  }

  def addMembers(id: GroupId) = DBAction(parse.urlFormEncoded) { implicit rs =>
    rs.request.body.get("member").foreach { ids =>
      groupService.addMembers(id, (ids map (id => MemberId(id.toLong))).toList)
    }

    Redirect(routes.GroupDetails.view(id)).flashing("success" -> "Member has been added")
  }

  def ajaxMembers(id: GroupId) = DBAction { implicit rs =>
    Select2.parse(rs.request).map {
      query => {
        val result = memberService.page(query.page, query.limit, filter = Some(query.query + '%'))

        Ok(Select2.respond(result, (m: Member) => m.id.toString, (m: Member) => m.name))
      }
    }.getOrElse(BadRequest("Missing parameters"))
  }
}
