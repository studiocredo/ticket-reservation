package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities._

import models.{MemberDetail, Page}
import scala.slick.session.Session
import models.ids._

class MemberService {
  val groupService = new GroupsService
  import models.queries._
  import models.schema.tables._

  val MembersQ = Query(Members)

  val active = MembersQ.filter(_.archived === false)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Member] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active, page, pageSize)
    } {
      (query, filter) => query.filter(_.name.like(filter)) // should replace with lucene
    }.run
    Page(values, page, pageSize, offset, total)
  }


  def insert(member: MemberEdit)(implicit s: Session): MemberId = {
    Members.autoInc.insert(member)
  }

  def update(id: MemberId, member: MemberEdit)(implicit s: Session) = {
    MembersQ.filter(_.id === id).update(toEntity(id, member))
  }

  def get(id: MemberId)(implicit s: Session): Option[Member] = {
    MembersQ.filter(_.id === id).firstOption
  }

  def getEdit(id: MemberId)(implicit s: Session): Option[MemberEdit] = get(id).map(toEdit)

  def toEdit(m: Member) = MemberEdit(m.name, m.email, m.address, m.phone, m.archived)
  def toEntity(id: MemberId, m: MemberEdit) =  Member(id, m.name, m.email, m.address, m.phone, m.archived)

  def delete(id: MemberId)(implicit s: Session) = {
    MembersQ.filter(_.id === id).delete
  }

  def memberDetails(id: MemberId)(implicit s: Session): Option[MemberDetail] = {
    for {
      member <- get(id)
    } yield MemberDetail(member, groupService.listForMemeber(member.id))
  }

}
