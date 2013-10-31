package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities.Member

import models.Page
import scala.slick.session.Session
import models.ids.{GroupId, MemberId}

class MemberService {

  import models.queries._
  import models.schema.tables._

  val MQuery = Query(Members)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Member] = {
    val offset = pageSize * page
    val total = MQuery.length.run
    val values = filter.foldLeft {
      paginate(MQuery, page, pageSize)
    } {
      (query, filter) => query.filter(_.name.like(filter)) // should replace with lucene
    }.run
    Page(values, page, pageSize, offset, total)
  }


  def insert(member: Member)(implicit s: Session): MemberId = {
    require(member.id == None)
    Members.forInsert.returning(Members.id).insert(member)
  }

  def update(id: MemberId, member: Member)(implicit s: Session) = {
    MQuery.filter(_.id === id).update(member.copy(id = Some(id)))
  }

  def get(id: MemberId)(implicit s: Session): Option[Member] = {
    MQuery.filter(_.id === id).firstOption
  }


  def delete(id: MemberId)(implicit s: Session) = {
    MQuery.filter(_.id === id).delete
  }


  def listForGroup(id: GroupId)(implicit s: Session): List[Member] = {
    (for {(jt, m) <- GroupMembers leftJoin Members on (_.memberId === _.id) if jt.groupId === id} yield m).sortBy(_.name.asc).run.toList
  }
}
