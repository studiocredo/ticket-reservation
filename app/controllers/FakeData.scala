package controllers

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.{MemberService, GroupsService, CourseService}
import play.api.Play.current
import models.entities._

object FakeData extends Controller {
  val memberService = new MemberService()
  val courseService = new CourseService()
  val groupService = new GroupsService()

  def insert() = DBAction { implicit rs =>
    val thomas = memberService.insert(MemberEdit("Thomas", Some("thomas@example.com"), None, None, archived = false))
    val sven = memberService.insert(MemberEdit("sven", Some("sven@example.com"), None, None, archived = false))
    val jantje = memberService.insert(MemberEdit("Jantje", None, Some("veldstraat 20 gent"), Some("09/2345435453435"), archived = false))

    val course1 = courseService.insert(CourseEdit("Indian rain dancing", archived = false))
    val course2 = courseService.insert(CourseEdit("Advanced roboting", archived = false))

    groupService.insert(GroupEdit("class A", 2010, course1))
    val classA = groupService.insert(GroupEdit("class A", 2013, course1))
    val classB = groupService.insert(GroupEdit("class B", 2013, course1))
    val classD = groupService.insert(GroupEdit("class D", 2014, course2))

    groupService.addMembers(classA, List(thomas, sven, jantje))
    groupService.addMembers(classB, List(thomas, jantje))
    groupService.addMembers(classD, List(jantje))

    Ok(views.html.index())
  }
}

