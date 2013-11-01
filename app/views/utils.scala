package views

import scala._
import models.entities.Course

object utils {

  def toOptions(courses: Seq[Course]): Seq[(String, String)] = {
    courses map { course => (course.id.toString, course.name)}
  }

}
