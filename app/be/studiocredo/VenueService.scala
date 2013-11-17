package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import com.google.inject.Inject
import models.admin._

class VenueService @Inject()() {
  import models.queries._
  import models.schema.tables._

  val VenuesQ = Query(Venues)

  val active = VenuesQ.filter(_.archived === false)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Venue] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active, page, pageSize)
    } {
      (query, filter) => query.filter(q => iLike(q.name, filter)) // should replace with lucene
    }.run
    Page(values, page, pageSize, offset, total)
  }

  def list()(implicit s: Session) = active.list

  def insert(member: VenueEdit)(implicit s: Session): VenueId = {
    Venues.autoInc.insert(member)
  }

  def update(id: VenueId, member: VenueEdit)(implicit s: Session) = {
    VenuesQ.filter(_.id === id).update(toEntity(id, member))
  }

  def get(id: VenueId)(implicit s: Session): Option[Venue] = {
    VenuesQ.filter(_.id === id).firstOption
  }

  def getEdit(id: VenueId)(implicit s: Session): Option[VenueEdit] = get(id).map(toEdit)

  def toEdit(m: Venue) = VenueEdit(m.name, m.description, m.archived)
  def toEntity(id: VenueId, m: VenueEdit) =  Venue(id, m.name, m.description, m.archived)

  def delete(id: VenueId)(implicit s: Session) = {
    VenuesQ.filter(_.id === id).delete
  }


}
