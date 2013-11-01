package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities._

import models.{GroupDetail, Page}
import scala.slick.session.Session
import models.ids.{MemberId, CourseId, GroupId}

class GroupsService {

  import models.queries._
  import models.schema.tables._

  val courseService = new CourseService()
  val memberService = new MemberService()

  val GroupsQ = Query(Groups)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1)(implicit s: Session): Page[Group] = {
    val offset = pageSize * page
    val total = GroupsQ.length.run
    val values = paginate(GroupsQ, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }

  def insert(group: GroupEdit)(implicit s: Session): GroupId = {
    Groups.autoInc.insert(group)
  }

  def update(id: GroupId, edit: GroupEdit)(implicit s: Session) = {
    GroupsQ.filter(_.id === id).update(toEntity(id, edit))
  }


  def get(id: GroupId)(implicit s: Session): Option[Group] = {
    GroupsQ.filter(_.id === id).firstOption
  }
  def getEdit(id: GroupId)(implicit s: Session): Option[GroupEdit] = get(id) map toEdit

  def delete(id: GroupId)(implicit s: Session) = {
    GroupsQ.filter(_.id === id).delete
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


  // MAGIC
  import shapeless._
  val entEditGen = Generic[GroupEdit]
  val entGen = Generic[Group]

  def toEntity(id: GroupId, edit: GroupEdit): Group = {
    entGen.from(id :: entEditGen.to(edit))
  }

  def toEdit(group:Group): GroupEdit = {
    entEditGen.from(entGen.to(group).tail)
  }
}

