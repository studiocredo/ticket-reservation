package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities.Group

import models.{GroupDetail, Page}
import scala.slick.session.Session
import models.ids.{MemberId, CourseId, GroupId}

class GroupsService {

  import models.queries._
  import models.schema.tables._
  val courseService = new CourseService()
  val memberService = new MemberService()

  val MQuery = Query(Groups)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1)(implicit s: Session): Page[Group] = {
    val offset = pageSize * page
    val total = MQuery.length.run
    val values = paginate(MQuery, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }


  def insert(group: Group)(implicit s: Session): GroupId = {
    require(group.id == None)
    Groups.forInsert.returning(Groups.id).insert(group)
  }

  def update(id: GroupId, group: Group)(implicit s: Session) = {
    MQuery.filter(_.id === id).update(group.copy(id = Some(id)))
  }

  def get(id: GroupId)(implicit s: Session): Option[Group] = {
    MQuery.filter(_.id === id).firstOption
  }

  def delete(id: GroupId)(implicit s: Session) = {
    MQuery.filter(_.id === id).delete
  }


  def listForCourse(id: CourseId)(implicit s: Session): List[Group] = {
    (for {c <- Groups if c.courseId === id} yield c).sortBy(_.year.desc).run.toList
  }

  def addMembers(id: GroupId, members: List[MemberId])(implicit s: Session) {
    members foreach {
      memberId =>
        if (!Query(GroupMembers).filter(_.groupId === id).filter(_.memberId === memberId).exists.run) {
          GroupMembers.insert((id, memberId))
        }
    }
  }

  def groupDetails(id: GroupId)(implicit s: Session): Option[GroupDetail] = {
    for {
      group <- get(id)
      course <- courseService.get(group.course)
    } yield GroupDetail(group, course, memberService.listForGroup(id))
  }
}
