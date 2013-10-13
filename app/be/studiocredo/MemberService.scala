package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities.{MemberId, Member}
import models.Page
import scala.slick.session.Session

class MemberService {

  import models.queries._

  import models.schema.tables.Members
  import models.tables.{Members => MemberQ }

  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%")(implicit s: Session): Page[Member] = {
    val offset = pageSize * page
    val total = MemberQ.length.run
    val values = MemberQ.paginate(page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }



  def insert(member: Member)(implicit s: Session): MemberId = {
    require(member.id == None)
    Members.forInsert returning Members.id insert member
  }

}
