package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities._

import models.Page
import scala.slick.session.Session
import models.ids.CourseId
import com.google.inject.Inject

class CourseService @Inject()() {

  import models.queries._
  import models.schema.tables._

  val CoursesQ = Query(Courses)

  val active = CoursesQ.filter(_.archived === false)

  def list()(implicit s: Session): Seq[Course] = {
    active.list
  }

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1)(implicit s: Session): Page[Course] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = paginate(active, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }

  def insert(course: CourseEdit)(implicit s: Session): CourseId = {
    Courses.autoInc.insert(course)
  }

  def update(id: CourseId, course: CourseEdit)(implicit s: Session) = {
    CoursesQ.filter(_.id === id).update(toEntity(id, course))
  }

  def get(id: CourseId)(implicit s: Session): Option[Course] = {
    CoursesQ.filter(_.id === id).firstOption
  }
  def getEdit(id: CourseId)(implicit s: Session): Option[CourseEdit] = get(id).map(toEdit)

  def toEdit(c: Course) = CourseEdit(c.name, c.archived)
  def toEntity(id: CourseId, c: CourseEdit) =  Course(id, c.name, c.archived)

  def delete(id: CourseId)(implicit s: Session) = {
    CoursesQ.filter(_.id === id).delete
  }
}
