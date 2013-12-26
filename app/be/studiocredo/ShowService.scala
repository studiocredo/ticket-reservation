package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import org.joda.time.DateTime
import com.github.tototoshi.slick.JodaSupport._
import be.studiocredo.util.Joda
import models.admin.{ShowEdit, VenueShows}
import scala.collection.{immutable, mutable}
import scala.collection.mutable.Builder

class ShowService {
  import models.queries._
  import models.schema.tables._

  val ShowsQ = Query(Shows)

  val active = ShowsQ.filter(_.archived === false)

  def page(page: Int = 0, pageSize: Int = 10)(implicit s: Session): Page[Show] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = paginate(active, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }


  def get(id: ShowId)(implicit s: Session): Option[Show] =  byId(id).firstOption
  def insert(id: EventId, show: ShowEdit)(implicit s: Session): ShowId = Shows.autoInc.insert((id, show.venueId, show.date))
  def update(id: ShowId, show: ShowEdit)(implicit s: Session) =  byId(id).map(_.edit).update((show.venueId, show.date))

  def delete(id: ShowId)(implicit s: Session) = (for (v <- ShowsQ if v.id === id) yield v.archived).update(true)


  def listForEvent(id: EventId)(implicit s: Session): List[VenueShows] = {
    import Joda._
    val q = for (
      s <- active.sortBy(_.date);
      v <- s.venue if s.eventId === id
    ) yield (s, v)

    q.list.groupBy(_._2).map(mv => VenueShows(mv._1, mv._2.map(_._1))).toList
  }

  def nextShows(limit: Int)(implicit s: Session): List[ShowOverview] = {
    val next = active.filter(_.date >= DateTime.now())
    val list = (for (s <- next; e <- s.event) yield (s, e)).sortBy(_._1.date).take(limit).list

    list map {
      case (show, event) => ShowOverview(event.name, show.date, show.id, event.id)
    }
  }

  private def byId(id: ids.ShowId)=  ShowsQ.where(_.id === id)
}
