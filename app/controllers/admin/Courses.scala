package controllers.admin

import play.api.mvc._
import be.studiocredo.{UserService, GroupsService, CourseService}
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities._
import com.google.inject.Inject
import be.studiocredo.auth._
import models.entities.CourseEdit
import scala.Some
import be.studiocredo.auth.SecuredDBRequest


class Courses @Inject()(courseService: CourseService, groupService: GroupsService,val authService: AuthenticatorService) extends AdminController {

  val ListPage = Redirect(routes.Courses.list())

  val courseForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "archived" -> boolean
    )(CourseEdit.apply)(CourseEdit.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs  =>
    val list = courseService.page(page)

    Ok(views.html.admin.courses(list))
  }

  def create() = AuthAction { implicit rs =>
    Ok(views.html.admin.coursesCreateForm(courseForm))
  }
  def save() = AuthDBAction { implicit rs =>
    courseForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.coursesCreateForm(formWithErrors)),
      course => {
        courseService.insert(course)

        ListPage.flashing("success" -> s"Course '${course.name}' has been created")
      }
    )
  }

  def edit(id: CourseId) = AuthDBAction { implicit rs =>
    courseService.getEdit(id) match {
      case None => ListPage
      case Some(course) => Ok(views.html.admin.coursesEditForm(id.id, courseForm.fillAndValidate(course)))
    }
  }

  def update(id: CourseId) = AuthDBAction(Authorization.ADMIN) { implicit rs =>
    courseForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.coursesEditForm(id, formWithErrors)),
      course => {
        courseService.update(id, course)

        ListPage.flashing("success" -> s"Course '${course.name}'  has been updated")
      }
    )
  }
  def delete(id: CourseId) = AuthDBAction { implicit rs =>
    courseService.delete(id)

    ListPage.flashing("success" -> "Course has been deleted")
  }


  def view(id: CourseId) = AuthDBAction { implicit rs =>
    courseService.get(id) match {
      case None => BadRequest(s"No course found with id $id")
      case Some(course) => {
        Ok(views.html.admin.course(course, groupService.listForCourse(id)))
      }
    }
  }

}
