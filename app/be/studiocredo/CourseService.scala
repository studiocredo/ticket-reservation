package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities.{Group, Course}

import models.Page
import scala.slick.session.Session
import models.ids.CourseId

class CourseService {

  import models.queries._
  import models.schema.tables._

  val MQuery = Query(Courses)


  def list()(implicit s: Session): Seq[Course] = {
    MQuery.filter(_.active === true).list
  }

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1)(implicit s: Session): Page[Course] = {
    val offset = pageSize * page
    val total = MQuery.length.run
    val values = paginate(MQuery, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }

  def insert(course: Course)(implicit s: Session): CourseId = {
    require(course.id == None)
    Courses.forInsert.returning(Courses.id).insert(course)
  }

  def update(id: CourseId, course: Course)(implicit s: Session) = {
    MQuery.filter(_.id === id).update(course.copy(id = Some(id)))
  }

  def get(id: CourseId)(implicit s: Session): Option[Course] = {
    MQuery.filter(_.id === id).firstOption
  }

  def delete(id: CourseId)(implicit s: Session) = {
    MQuery.filter(_.id === id).delete
  }
}
