package models

import models.entities.{Member, Course, Group}

case class GroupDetail(group: Group, course: Course, members: List[Member]) {
  def id = group.id.get
}
