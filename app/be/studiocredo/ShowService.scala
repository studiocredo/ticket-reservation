package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._

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

  def insert(member: ShowEdit)(implicit s: Session): ShowId = {
    Shows.autoInc.insert(member)
  }

  def update(id: ShowId, member: ShowEdit)(implicit s: Session) = {
    ShowsQ.filter(_.id === id).update(toEntity(id, member))
  }

  def get(id: ShowId)(implicit s: Session): Option[Show] = {
    ShowsQ.filter(_.id === id).firstOption
  }

  def getEdit(id: ShowId)(implicit s: Session): Option[ShowEdit] = get(id).map(toEdit)

  def toEdit(m: Show) = ShowEdit(m.eventId, m.venueId, m.date, m.archived)
  def toEntity(id: ShowId, m: ShowEdit) =  Show(id, m.eventId, m.venueId, m.date, m.archived)

  def delete(id: ShowId)(implicit s: Session) = {
    ShowsQ.filter(_.id === id).delete
  }


  def listForEvent(id: EventId)(implicit s: Session): Map[Venue, List[Show]] = {
    val list = (for (s <- Shows; v <- s.venue if s.eventId === id) yield (s, v)).list

    list.groupBy(_._2).mapValues(_.map(_._1))
  }

}
