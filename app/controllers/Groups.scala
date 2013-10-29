package controllers

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.{CourseService, GroupsService}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities.{Course, Group}
import views.helper.Options
import scala.slick.session.Session

object Groups extends Controller {
  val groupService = new GroupsService()
  val courseService = new CourseService()

  val ListPage = Redirect(routes.Groups.list())

  val groupForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "year" -> number,
      "course" -> of[CourseId]
    )
    ({ (name, year, course) => Group(None, name, year, course)})
    ({ group => Some(group.name, group.year, group.course)})
  )

  def list(page: Int) = DBAction { implicit rs =>
    val list = groupService.page(page)
    Ok(views.html.groups(list))
  }


  def create() = DBAction { implicit rs =>
    Ok(views.html.groupsCreateForm(groupForm, courseOptions))
  }
  def save() = DBAction { implicit rs =>
    groupForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.groupsCreateForm(formWithErrors, courseOptions)),
      group => {
        groupService.insert(group)

        ListPage.flashing("success" -> "Group %s has been created".format(group.name))
      }
    )
  }

  def edit(id: Long) = DBAction { implicit rs =>
    groupService.get(id) match {
      case None => ListPage
      case Some(group) => Ok(views.html.groupsEditForm(id, groupForm.fillAndValidate(group), courseOptions))
    }
  }
  def update(id: Long) = DBAction { implicit rs =>
    groupForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.groupsEditForm(id, formWithErrors, courseOptions)),
      group => {
        groupService.update(id, group)

        ListPage.flashing("success" -> "Group %s has been updated".format(group.name))
      }
    )
  }
  def delete(id: Long) = DBAction { implicit rs =>
    groupService.delete(id)

    ListPage.flashing("success" -> "Group has been deleted")
  }


  private def courseOptions(implicit s: Session): Options[Course] = Options(courseService.list(), Options.CourseRenderer)
}
