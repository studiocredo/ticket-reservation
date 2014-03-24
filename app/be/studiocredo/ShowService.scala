package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import org.joda.time.DateTime
import com.github.tototoshi.slick.JodaSupport._
import be.studiocredo.util.Joda
import models.admin.{ShowEdit, VenueShows}
import scala.collection.{mutable, immutable}
import scala.collection.mutable.Builder
import com.google.inject.Inject
import models.entities.SeatType.SeatType

class ShowService @Inject()(venueService: VenueService, preReservationService: PreReservationService) {
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

  def nextShows(limit: Int)(implicit s: Session): List[ShowAvailability] = {
    val next = active.filter(_.date >= DateTime.now())
    val list = (for (s <- next; e <- s.event; v <- s.venue) yield (s, e, v)).sortBy(_._1.date).take(limit).list

    list map {
      case (show, event, venue) => preReservationService.availability(EventShow(show.id, event.id, event.name, show.venueId, venue.name, show.date, show.archived))
    }
  }

  def listReservable(implicit s: Session): List[ShowId] = {
    val q = for (
      s <- active;
      e <- s.event;
      v <- s.venue
      if v.floorplan.isNotNull
    ) yield (s.id, e.reservationStart, e.reservationEnd)
    q.list.collect{ case (sid: ShowId, rStart: Option[DateTime], rEnd: Option[DateTime]) if (rStart.isEmpty || (rStart.get.minusHours(1).isBeforeNow() && rEnd.get.plusHours(1).isAfterNow()))=> sid}
  }

  def getEventShow(id: ShowId)(implicit s: Session): EventShow = {
    val q = for (s <- byId(id); e <- s.event; v <- s.venue) yield (s.id, e.id, e.name, s.venueId, v.name, s.date, s.archived)
    (EventShow.apply _) tupled q.first
  }

  private def byId(id: ids.ShowId)=  ShowsQ.where(_.id === id)
}
