package controllers

import play.api.db.slick._
import be.studiocredo.{MemberService, CourseService, GroupsService}
import play.api.Play.current
import models.ids._
import play.api._
import play.api.mvc._
import models.entities.{Group, Member}


object MemberDetails extends Controller {
  val logger = Logger("group-details")
  val groupService = new GroupsService()
  val courseService = new CourseService()
  val memberService = new MemberService()


  def view(id: MemberId) = DBAction { implicit rs =>
    memberService.memberDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for member $id")
      case Some(details) => Ok(views.html.member(details))
    }
  }

  def addGroups(memberId: MemberId) = DBAction(parse.urlFormEncoded) { implicit rs =>
    val groups = rs.request.body.get("class")
    Logger.debug(s"add member $memberId to group $groups")
    groups.foreach(ids => ids.map(id => GroupId(id.toInt)).foreach (id => groupService.addMembers(id, memberId)))

    Redirect(routes.MemberDetails.view(memberId)).flashing("success" -> "Added to class")
  }

  def ajaxGroups(id: MemberId) = DBAction { implicit rs =>
    Select2.parse(rs.request).map {
      query => {
        val result = groupService.page(query.page, query.limit, filter = Some(query.query + '%'))

        Ok(Select2.respond(result, (g: Group) => g.id.toString, (g: Group) => s"(${g.year}) ${g.name}"))
      }
    }.getOrElse(BadRequest("Missing parameters"))
  }
}
