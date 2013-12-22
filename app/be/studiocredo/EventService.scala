package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import com.google.inject.Inject
import models.admin._

class EventService @Inject()(showService: ShowService) {
  import models.queries._
  import models.schema.tables._

  val EventsQ = Query(Events)

  val active = EventsQ.filter(_.archived === false)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Event] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active, page, pageSize)
    } {
      (query, filter) => query.filter(q => iLike(q.name, filter)) // should replace with lucene
    }.run
    Page(values, page, pageSize, offset, total)
  }

  def insert(event: EventEdit)(implicit s: Session): EventId = Events.autoInc.insert(event)
  def update(id: EventId, event: EventEdit)(implicit s: Session) = editById(id).update(event)

  def get(id: EventId)(implicit s: Session): Option[Event] = byId(id).firstOption
  def getEdit(id: EventId)(implicit s: Session): Option[EventEdit] = editById(id).firstOption

  def delete(id: EventId)(implicit s: Session) = (for (v <- EventsQ if v.id === id) yield v.archived).update(true)

  def eventDetails(id: EventId)(implicit s: Session): Option[EventDetail] = {
    get(id).map{(event) => EventDetail(event, showService.listForEvent(event.id))}
  }

  private def byId(id: ids.EventId)=  EventsQ.where(_.id === id)
  private def editById(id: ids.EventId) = byId(id).map(_.edit)
}
