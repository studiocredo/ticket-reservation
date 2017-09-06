package be.studiocredo

import be.studiocredo.util.Joda
import com.github.tototoshi.slick.JodaSupport._
import com.google.inject.Inject
import models._
import models.admin.{AssetEdit, RichAsset, VenueShows}
import models.entities._
import models.ids._
import models.schema.Assets
import org.joda.time.DateTime
import play.api.db.slick.Config.driver.simple._

class AssetService @Inject()() {

  import models.queries._
  import models.schema.tables._

  val AssetsQ = Query(Assets)

  val active: Query[schema.Assets, Asset] = AssetsQ.filter(_.archived === false)

  def page(page: Int = 0, pageSize: Int = 10)(implicit s: Session): Page[Asset] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = paginate(active, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }


  def get(id: AssetId)(implicit s: Session): Option[Asset] = byId(id).firstOption

  def getEdit(id: AssetId)(implicit s: Session): Option[AssetEdit] = editById(id).firstOption

  def insert(id: EventId, asset: AssetEdit)(implicit s: Session): AssetId = Assets.autoInc.insert((id, asset.name, asset.price, asset.availableStart, asset.availableEnd, asset.downloadable, asset.objectKey, asset.archived))

  def update(id: AssetId, asset: AssetEdit)(implicit s: Session): Int = editById(id).update(asset)

  def delete(id: AssetId)(implicit s: Session): Int = (for (v <- AssetsQ if v.id === id) yield v.archived).update(true)


  def listAvailable(userId: Option[UserId])(implicit s: Session): Option[List[RichAsset]] = {
    userId.map { _ =>
      val q = for (
        a <- available.sortBy(sortFunction);
        e <- a.event
      ) yield (e, a)
      q.list.map { case (e, a) => RichAsset.init(e, a) }
    }
  }

  def listActiveForEvent(id: EventId)(implicit s: Session): List[Asset] = {
    available.sortBy(sortFunction).where(_.eventId === id).list()
  }

  def listAllForEvent(id: EventId)(implicit s: Session): List[Asset] = {
    AssetsQ.sortBy(sortFunction).where(_.eventId === id).list()
  }

  def listActive(implicit s: Session): Set[AssetId] = {
    val q = for (
      s <- active;
      e <- s.event if !e.archived
    ) yield s.id
      q.to[Set]
  }

  private def byId(id: ids.AssetId) = AssetsQ.where(_.id === id)

  private def editById(id: ids.AssetId) = byId(id).map(_.edit)

  private def sortFunction: (Assets) => scala.slick.lifted.Ordered = { m => (m.availableStart, m.availableEnd, m.eventId, m.name) }

  private def available = {
    val now = DateTime.now()
    active.filter(_.availableStart <= now).filter { m =>
      m.availableEnd.isNull || m.availableEnd >= now
    }
  }
}
