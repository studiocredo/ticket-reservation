package models

import models.entities._

case class MemberDetail(member: Member, groups: List[Group]) {
  def id = member.id
}
