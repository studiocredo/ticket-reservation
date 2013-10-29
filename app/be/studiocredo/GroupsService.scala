package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities.Group

import models.Page
import scala.slick.session.Session
import models.ids.GroupId

class GroupsService {

  import models.queries._
  import models.schema.tables._

  val MQuery = Query(Groups)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%")(implicit s: Session): Page[Group] = {
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
}
