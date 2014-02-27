package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import com.google.inject.Inject
import models.admin._
import be.studiocredo.util.ServiceReturnValues._
import org.joda.time.DateTime
import be.studiocredo.util.Joda._

class EventService @Inject()(showService: ShowService, preResevationService: PreReservationService, userService: UserService) {
  import models.queries._
  import models.schema.tables._

  val EventsQ = Query(Events)
  val EventPricesQ = Query(EventPrices)

  val active = EventsQ.filter(_.archived === false)

  def page(page: Int = 0, pageSize: Int = 10, filter: Option[String] = None)(implicit s: Session): Page[Event] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active.sortBy(_.id.desc), page, pageSize)
    } {
      (query, filter) => query.filter(q => iLike(q.name, filter)) // should replace with lucene
    }.run
    Page(values, page, pageSize, offset, total)
  }

  def insert(event: EventEdit)(implicit s: Session): Either[ServiceFailure, EventId] = {
    validateEvent(event).fold(
      error => Left(error),
      success => Right(Events.autoInc.insert(event))
    )
  }

  def update(id: EventId, event: EventEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateEvent(event).fold(
      error => Left(error),
      success => {
        editById(id).update(event)
        Right(serviceSuccess("event.save.success"))
      }
    )
  }

  def addPrice(pricing: EventPrice)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateEventPrice(pricing).fold(
      error => Left(error),
      success => {
        EventPrices.*.insert(pricing)
        Right(serviceSuccess("eventprice.save.success"))
      }
    )
  }

  def updatePrice(pricing: EventPrice)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateEventPrice(pricing).fold(
      error => Left(error),
      success => {
        (for {
          ep <- EventPricesQ
          if ep.eventId === pricing.id
          if ep.priceCategory === pricing.category
        } yield (ep.price)).update(pricing.price)
        Right(serviceSuccess("eventprice.save.success"))
      }
    )
  }

  def list()(implicit s: Session): List[Event] = active.list

  def listUpcoming()(implicit s: Session): List[Event] = {
    val query = for {
      s <- Query(Shows)
      if s.archived === false
      e <- s.event
      if e.archived === false
    } yield (e, s.date)
    query.list.sortBy(_._2).collect{case (e, d) if d.isAfterNow => e}.distinct
  }

  def get(id: EventId)(implicit s: Session): Option[Event] = byId(id).firstOption
  def getEdit(id: EventId)(implicit s: Session): Option[EventEdit] = editById(id).firstOption

  def delete(id: EventId)(implicit s: Session) = (for (v <- EventsQ if v.id === id) yield v.archived).update(true)

  def eventDetails(id: EventId)(implicit s: Session): Option[EventDetail] = {
    get(id).map{(event) => EventDetail(event, showService.listForEvent(event.id), getPricing(id))}
  }

  def getPricing(id: EventId)(implicit s: Session): Option[EventPricing] = {
    val prices = EventPricesQ.where(_.eventId === id).list
    if (prices.isEmpty) None else Some(EventPricing(id, prices))
  }

  def eventPrereservationDetails(id: EventId, users: List[UserId])(implicit s: Session): Option[EventPrereservationsDetail] = {
    get(id).map{(event) => EventPrereservationsDetail(event, userService.findUsers(users), showService.listForEvent(event.id), preResevationService.preReservationsByUsers(users), preResevationService.quotaByUsers(users))}
  }

  def eventReservationDetails(id: EventId, users: List[UserId])(implicit s: Session): Option[EventReservationsDetail] = {
    get(id).map{(event) => EventReservationsDetail(EventDetail(event, showService.listForEvent(event.id), getPricing(id)), userService.findUsers(users), showService.listForEvent(event.id), preResevationService.pendingPrereservationsByUsersAndEvent(users, event.id))}
  }

  private def byId(id: ids.EventId)=  EventsQ.where(_.id === id)
  private def editById(id: ids.EventId) = byId(id).map(_.edit)

  private def validateEvent(event: EventEdit): Either[ServiceFailure, ServiceSuccess] = {
    validateBeforeOrUndefined(event.preReservationStart, event.preReservationEnd, "preres").fold(
      error => Left(error),
      success => {
        validateBeforeOrUndefined(event.reservationStart, event.reservationEnd, "res").fold(
          error => Left(error),
          success => Right(success)
        )
      }
    )
  }

  private def validateBeforeOrUndefined(start: Option[DateTime], end: Option[DateTime], key: String): Either[ServiceFailure, ServiceSuccess] = {
    if (start.isDefined && end.isDefined) {
      if (!start.get.isBefore(end.get)) {
        return Left(serviceFailure(s"event.save.$key.before"))
      }
    } else if (start.isDefined || end.isDefined) {
      return Left(serviceFailure(s"event.save.$key.both"))
    }
    return Right(serviceSuccess(s"event.save.$key.success"))
  }

  private def validateEventPrice(pricing: EventPrice): Either[ServiceFailure, ServiceSuccess] = {
    pricing.price match {
      case price if price.amount < 0 => Left(serviceFailure("eventprice.save.positive"))
      case _ => Right(serviceSuccess("eventprice.save.success"))
    }
  }
}
