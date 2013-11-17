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

  def insert(member: EventEdit)(implicit s: Session): EventId = {
    Events.autoInc.insert(member)
  }

  def update(id: EventId, member: EventEdit)(implicit s: Session) = {
    EventsQ.filter(_.id === id).update(toEntity(id, member))
  }

  def get(id: EventId)(implicit s: Session): Option[Event] = {
    EventsQ.filter(_.id === id).firstOption
  }

  def getEdit(id: EventId)(implicit s: Session): Option[EventEdit] = get(id).map(toEdit)

  def toEdit(m: Event) = EventEdit(m.name, m.description, m.archived)
  def toEntity(id: EventId, m: EventEdit) =  Event(id, m.name, m.description, m.archived)

  def delete(id: EventId)(implicit s: Session) = {
    EventsQ.filter(_.id === id).delete
  }


  def eventDetails(id: EventId)(implicit s: Session): Option[EventDetail] = {
    get(id).map{(event) => EventDetail(event, showService.listForEvent(event.id))}

  }

}
