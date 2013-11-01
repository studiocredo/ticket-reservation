package controllers

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.{GroupsService, CourseService}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities._

object Courses extends Controller {
  val courseService = new CourseService()
  val groupService = new GroupsService()

  val ListPage = Redirect(routes.Courses.list())

  val courseForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "archived" -> boolean
    )(CourseEdit.apply)(CourseEdit.unapply)
  )

  def list(page: Int) = DBAction { implicit rs =>
    val list = courseService.page(page)
    Ok(views.html.courses(list))
  }

  def create() = Action { implicit request =>
    Ok(views.html.coursesCreateForm(courseForm))
  }
  def save() = DBAction { implicit rs =>
    courseForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.coursesCreateForm(formWithErrors)),
      course => {
        courseService.insert(course)

        ListPage.flashing("success" -> s"Course ${course.name} has been created")
      }
    )
  }

  def edit(id: CourseId) = DBAction { implicit rs =>
    courseService.getEdit(id) match {
      case None => ListPage
      case Some(course) => Ok(views.html.coursesEditForm(id.id, courseForm.fillAndValidate(course)))
    }
  }
  def update(id: CourseId) = DBAction { implicit rs =>
    courseForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.coursesEditForm(id, formWithErrors)),
      course => {
        courseService.update(id, course)

        ListPage.flashing("success" -> s"Course ${course.name}  has been updated")
      }
    )
  }
  def delete(id: CourseId) = DBAction { implicit rs =>
    courseService.delete(id)

    ListPage.flashing("success" -> "Course has been deleted")
  }


  def view(id: CourseId) = DBAction { implicit rs =>
    courseService.get(id) match {
      case None => BadRequest(s"No course found with id $id")
      case Some(course) => {
        Ok(views.html.course(course, groupService.listForCourse(id)))
      }
    }
  }

}
