package controllers

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.{MemberService, GroupsService, CourseService}
import play.api.Play.current
import models.entities.{Group, Course, Member}

object FakeData extends Controller {
  val memberService = new MemberService()
  val courseService = new CourseService()
  val groupService = new GroupsService()

  def insert() = DBAction { implicit rs =>
    val thomas = memberService.insert(Member(None, "Thomas", Some("thomas@example.com"), None, None, active = true))
    val sven = memberService.insert(Member(None, "sven", Some("sven@example.com"), None, None, active = true))
    val jantje = memberService.insert(Member(None, "Jantje", None, Some("veldstraat 20 gent"), Some("09/2345435453435"), active = true))

    val course1 = courseService.insert(Course(None, "Indian rain dancing", active = true))
    val course2 = courseService.insert(Course(None, "Advanced roboting", active = true))

    groupService.insert(Group(None, "class A", 2010, course1))
    val classA = groupService.insert(Group(None, "class A", 2013, course1))
    val classB = groupService.insert(Group(None, "class B", 2013, course1))
    val classD = groupService.insert(Group(None, "class D", 2014, course2))

    groupService.addMembers(classA, List(thomas, sven, jantje))
    groupService.addMembers(classB, List(thomas, jantje))
    groupService.addMembers(classD, List(jantje))

    Ok(views.html.index())
  }
}

