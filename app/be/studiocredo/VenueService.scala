package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import com.google.inject.Inject

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

  def insert(venue: VenueEdit)(implicit s: Session): VenueId = Venues.autoInc.insert(venue)
  def update(id: VenueId, venue: VenueEdit)(implicit s: Session) = editById(id).update(venue)
  def update(id: VenueId, floorPlan: FloorPlan)(implicit s: Session) = byId(id).map(_.floorplan).update(Some(floorPlan))

  def get(id: VenueId)(implicit s: Session): Option[Venue] = byId(id).firstOption
  def getEdit(id: VenueId)(implicit s: Session): Option[VenueEdit] = editById(id).firstOption

  def delete(id: VenueId)(implicit s: Session) = {
    (for (v <- VenuesQ if v.id === id) yield v.archived).update(true)

  }

  private def byId(id: ids.VenueId)=  VenuesQ.where(_.id === id)
  private def editById(id: ids.VenueId) = byId(id).map(_.edit)
}
