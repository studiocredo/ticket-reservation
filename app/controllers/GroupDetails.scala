package controllers

import play.api.db.slick._
import be.studiocredo.{MemberService, CourseService, GroupsService}
import play.api.Play.current
import models.ids._
import scala.slick.session.Session
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._


object GroupDetails extends Controller {
  val logger = Logger("group-details")
  val groupService = new GroupsService()
  val courseService = new CourseService()
  val memberService = new MemberService()


  def view(id: GroupId) = DBAction { implicit rs =>
    groupService.groupDetails(id) match {
      case None => BadRequest(s"Failed to retrieve details for class $id")
      case Some(details) => Ok(views.html.group(details))
    }
  }

  def addMembers(id: GroupId) = DBAction(parse.urlFormEncoded) { implicit rs =>
    logger.warn(rs.request.body.toString())

    rs.request.body.get("member").foreach { ids =>
      groupService.addMembers(id, (ids map (id => MemberId(id.toLong))).toList)
    }

    Redirect(routes.GroupDetails.view(id)).flashing("success" -> "Member has been added")
  }

  def ajaxMembers(id: GroupId) = DBAction { implicit rs =>
    implicit val rds = ((__ \ 'q).read[String] and (__ \ 'limit).read[Int] and (__ \ 'page).read[Int]).tupled

    val query = for {
      query <- rs.request.getQueryString("q")
      limit <- rs.request.getQueryString("limit") map (_.toInt)
      page <- rs.request.getQueryString("page") map (_.toInt)
    } yield {

      val result = memberService.page(Math.max(0, page -1), limit, filter = Some(query + '%'))

      Ok(Json.obj("total" -> result.total, "results" ->
        result.items.map (member => Json.obj("id" -> member.id.get.id, "text" -> member.name))
      ))
    }

    query.getOrElse(BadRequest("Missing parameters"))
  }
}
