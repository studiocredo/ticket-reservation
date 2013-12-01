package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import org.joda.time.DateTime
import com.github.tototoshi.slick.JodaSupport._
import be.studiocredo.util.Joda

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
  def getEdit(id: ShowId)(implicit s: Session): Option[ShowEdit] = editById(id).firstOption

  def insert(member: ShowEdit)(implicit s: Session): ShowId = Shows.autoInc.insert(member)
  def update(id: ShowId, member: ShowEdit)(implicit s: Session) = editById(id).update(member)


  def delete(id: ShowId)(implicit s: Session) = byId(id).delete


  def listForEvent(id: EventId)(implicit s: Session): Map[Venue, List[Show]] = {
    val list = (for (s <- active; v <- s.venue if s.eventId === id) yield (s, v)).list
    import Joda._
    list.groupBy(_._2).mapValues(_.map(_._1).sortBy(_.date))
  }

  def nextShows(limit: Int)(implicit s: Session): List[ShowOverview] = {
    val next = active.filter(_.date >= DateTime.now())
    val list = (for (s <- next; e <- s.event) yield (s, e)).sortBy(_._1.date).take(limit).list

    list map {
      case (show, event) => ShowOverview(event.name, show.date, show.id, event.id)
    }
  }

  private def byId(id: ids.ShowId)=  ShowsQ.where(_.id === id)
  private def editById(id: ids.ShowId) =  byId(id).map(_.edit)
}
