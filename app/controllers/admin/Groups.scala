package controllers.admin

import play.api.mvc._
import play.api.db.slick._
import be.studiocredo.{MemberService, CourseService, GroupsService}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import scala.Some
import models.entities._
import views.helper.Options
import scala.slick.session.Session
import org.joda.time.DateTime
import com.google.inject.Inject
import be.studiocredo.auth.AuthenticatorService

class Groups @Inject()(groupService: GroupsService, courseService: CourseService, memberService: MemberService, val authService: AuthenticatorService) extends AdminController {
  val ListPage = Redirect(routes.Groups.list())

  val groupForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "year" -> number,
      "course" -> of[CourseId],
      "archived" -> boolean
    )(GroupEdit.apply)(GroupEdit.unapply)
  )

  def list(page: Int) = AuthDBAction { implicit rs =>
    val list = groupService.page(page)
    Ok(views.html.admin.groups(list))
  }

  def createForCourse(id: CourseId) = AuthDBAction { implicit rs =>
    Ok(views.html.admin.groupsCreateForm(groupForm.fill(GroupEdit("", DateTime.now().year().get(), id, archived = false)), courseOptions))
  }

  def create() = AuthDBAction { implicit rs =>
    Ok(views.html.admin.groupsCreateForm(groupForm, courseOptions))
  }
  def save() = AuthDBAction { implicit rs =>
    groupForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.groupsCreateForm(formWithErrors, courseOptions)),
      group => {
        groupService.insert(group)

        ListPage.flashing("success" -> "Group '%s' has been created".format(group.name))
      }
    )
  }

  def edit(id: GroupId) = AuthDBAction { implicit rs =>
    groupService.getEdit(id) match {
      case None => ListPage
      case Some(group) => Ok(views.html.admin.groupsEditForm(id, groupForm.fillAndValidate(group), courseOptions))
    }
  }
  def update(id: GroupId) = AuthDBAction { implicit rs =>
    groupForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.admin.groupsEditForm(id, formWithErrors, courseOptions)),
      group => {
        groupService.update(id, group)

        ListPage.flashing("success" -> "Group '%s' has been updated".format(group.name))
      }
    )
  }
  def delete(id: GroupId) = AuthDBAction { implicit rs =>
    groupService.delete(id)

    ListPage.flashing("success" -> "Group has been deleted")
  }

  private def courseOptions(implicit s: Session): Options[Course] = Options(courseService.list(), Options.CourseRenderer)
}
