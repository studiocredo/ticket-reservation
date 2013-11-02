package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities._

import models.{GroupDetail, Page}
import scala.slick.session.Session
import models.ids.{MemberId, CourseId, GroupId}
import play.api.Logger

class GroupsService {

  import models.queries._
  import models.schema.tables._

  val courseService = new CourseService()

  val GroupsQ = Query(Groups)

  val active = GroupsQ.filter(_.archived === false).sortBy(_.year.desc)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Group] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active, page, pageSize)
    } {
      (query, filter) => query.filter(_.name.like(filter)) // should replace with lucene
    }.run
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

  def listForMemeber(id: MemberId)(implicit s: Session): List[Group] = {
    (for {(gm, g) <- GroupMembers leftJoin Groups on (_.groupId === _.id) if gm.memberId === id} yield g).sortBy(group => (group.year.desc, group.name.asc)).run.toList
  }

  def listMemberInGroup(id: GroupId)(implicit s: Session): List[Member] = {
    (for {(gm, m) <- GroupMembers leftJoin Members on (_.memberId === _.id) if gm.groupId === id} yield m).sortBy(_.name.asc).run.toList
  }


  def addMembers(id: GroupId, members: List[MemberId])(implicit s: Session) {
    members.foreach(addMembers(id, _))
  }

  def addMembers(groupId: GroupId, memberId: MemberId)(implicit s: Session) {
    Logger.debug(s"add member $memberId to group $groupId")
    if (!Query(GroupMembers).filter(_.groupId === groupId).filter(_.memberId === memberId).exists.run) {
      GroupMembers.insert((groupId, memberId))
    }
  }

  def groupDetails(id: GroupId)(implicit s: Session): Option[GroupDetail] = {
    for {
      group <- get(id)
      course <- courseService.get(group.course)
    } yield GroupDetail(group, course, listMemberInGroup(id))
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

