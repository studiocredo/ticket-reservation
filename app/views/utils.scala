package views

import scala._
import models.entities.Course

object utils {

  def toOptions(courses: Seq[Course]): Seq[(String, String)] = {
    courses filter (_.id.isDefined) map { course => (course.id.get.id.toString, course.name)}
  }

}
