package controllers

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.CourseService
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities.Course

object Courses extends Controller {
  val courseService = new CourseService()

  val ListPage = Redirect(routes.Courses.list())

  val courseForm = Form(
    mapping(
      "name" -> nonEmptyText
    )
    ({ (name) => Course(None, name, active = true)})
    ({ course => Some(course.name)})
  )

  def list(page: Int) = DBAction { implicit rs =>
    val list = courseService.page(page)
    Ok(views.html.courses(list))
  }


  def create() = Action {
    Ok(views.html.coursesCreateForm(courseForm))
  }
  def save() = DBAction { implicit rs =>
    courseForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.coursesCreateForm(formWithErrors)),
      course => {
        courseService.insert(course)

        ListPage.flashing("success" -> "Course %s has been created".format(course.name))
      }
    )
  }

  def edit(id: Long) = DBAction { implicit rs =>
    courseService.get(id) match {
      case None => ListPage
      case Some(course) => Ok(views.html.coursesEditForm(id, courseForm.fillAndValidate(course)))
    }
  }
  def update(id: Long) = DBAction { implicit rs =>
    courseForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.coursesEditForm(id, formWithErrors)),
      course => {
        courseService.update(id, course)

        ListPage.flashing("success" -> "Course %s has been updated".format(course.name))
      }
    )
  }
  def delete(id: Long) = DBAction { implicit rs =>
    courseService.delete(id)

    ListPage.flashing("success" -> "Course has been deleted")
  }
}
