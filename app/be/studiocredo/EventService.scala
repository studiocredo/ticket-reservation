package be.studiocredo

import be.studiocredo.util.Joda._
import be.studiocredo.util.Money
import be.studiocredo.util.ServiceReturnValues._
import com.google.inject.Inject
import models._
import models.admin._
import models.entities._
import models.entities.interfaces.Priced
import models.ids._
import org.joda.time.DateTime
import play.api.db.slick.Config.driver.simple._

class EventService @Inject()(showService: ShowService, preReservationService: PreReservationService, userService: UserService, assetService: AssetService) {

  import models.queries._
  import models.schema.tables._

  val EventsQ = Query(Events)
  val EventPricesQ = Query(EventPrices)

  val active: Query[schema.Events, Event] = EventsQ.filter(_.archived === false)

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

  def pageAll(page: Int = 0, pageSize: Int = 10, filter: Option[String] = None)(implicit s: Session): Page[Event] = {
    val offset = pageSize * page
    val total = EventsQ.length.run
    val values = filter.foldLeft {
      paginate(EventsQ.sortBy(_.id.desc), page, pageSize)
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

  def update(id: EventId, event: EventWithPriceEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateEvent(event.event).fold(
      error => Left(error),
      success => {
        s.withTransaction {
          editById(id).update(event.event)
          event.pricing.map(p => addOrUpdatePrice(id, p))
        }
        Right(serviceSuccess("event.save.success"))
      }
    )
  }

  def addOrUpdatePrice(id: EventId, pricing: List[EventPriceEdit])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    pricing.map {
      validateEventPrice
    }.foldLeft[Either[ServiceFailure, ServiceSuccess]](Right(serviceSuccess("eventprice.save.success"))) { (accumulatedStatus, outcome) =>
      outcome.fold(
        error => Left(error),
        success => accumulatedStatus
      )
    }.fold(
      error => Left(error),
      success => {
        val q = for {
          ep <- EventPricesQ
          if ep.eventId === id
          if ep.priceCategory inSetBind pricing.map(_.category)
        } yield ep.priceCategory
        val existingPriceCategories = q.list()

        val eventPrices = pricing.map(e => EventPrice(id, e.category, e.price))

        val (existingPrices, newPrices) = eventPrices.partition(p => existingPriceCategories.contains(p.category))
        val toberemovedPrices = EventPricesQ.where(_.eventId === id).list().filter(p => !existingPriceCategories.contains(p.category))

        s.withTransaction {
          val result = existingPrices.map(updatePrice) ++ newPrices.map(addPrice)
          toberemovedPrices.map(removePrice)

          result.foldLeft[Either[ServiceFailure, ServiceSuccess]](Right(serviceSuccess("eventprice.save.success"))) { (accumulatedStatus, outcome) =>
            outcome.fold(
              error => Left(error),
              success => accumulatedStatus
            )
          }
        }
      }
    )
  }

  def removePrice(pricing: EventPrice)(implicit s: Session): Int = {
    (for {
      ep <- EventPricesQ
      if ep.eventId === pricing.id
      if ep.priceCategory === pricing.category
    } yield ep).delete
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
        } yield ep.price).update(pricing.price)
        Right(serviceSuccess("eventprice.save.success"))
      }
    )
  }

  def list()(implicit s: Session): List[Event] = active.list

  def listUpcoming()(implicit s: Session): List[EventDetail] = {
    val query = for {
      s <- Query(Shows)
      if s.archived === false
      e <- s.event
      if e.archived === false
    } yield (e.id, s.date)
    query.list.sortBy(_._2).collect { case (eid, d) if d.isAfterNow => eid }.distinct.map {
      eventDetails(_).get
    }
  }

  def get(id: EventId)(implicit s: Session): Option[Event] = byId(id).firstOption

  def getEdit(id: EventId)(implicit s: Session): Option[EventEdit] = editById(id).firstOption

  def getEditWithPrice(id: EventId)(implicit s: Session): Option[EventWithPriceEdit] = editById(id).firstOption.map(EventWithPriceEdit.create(_, getPricing(id)))

  def delete(id: EventId)(implicit s: Session): Int = (for (v <- EventsQ if v.id === id) yield v.archived).update(true)

  def eventDetails(id: EventId)(implicit s: Session): Option[EventDetail] = {
    get(id).map { (event) => EventDetail(event, showService.listAllForEvent(event.id), getPricing(id), assetService.listAllForEvent(event.id)) }
  }

  def getPricing(id: EventId)(implicit s: Session): Option[EventPricing] = {
    val prices = EventPricesQ.where(_.eventId === id).list
    if (prices.isEmpty) None else Some(EventPricing(id, prices))
  }

  def getPricing(id: EventId, category: String)(implicit s: Session): Option[Money] = {
    EventPricesQ.where(_.eventId === id).where(_.priceCategory === category).firstOption.map(_.price)
  }

  def eventPrereservationDetails(id: EventId, users: List[UserId])(implicit s: Session): Option[EventPrereservationsDetail] = {
    get(id).map { (event) => EventPrereservationsDetail(event, userService.findUsers(users), showService.listActiveForEvent(event.id), preReservationService.preReservationsByUsersAndEvent(users, event.id), preReservationService.totalQuotaByUsersAndEvent(users, event.id)) }
  }

  def eventReservationDetails(id: EventId, users: List[UserId])(implicit s: Session): Option[EventReservationsDetail] = {
    get(id).map { (event) => EventReservationsDetail(EventDetail(event, showService.listActiveForEvent(event.id), getPricing(id), Nil /*Not needed*/), userService.findUsers(users), showService.listActiveForEvent(event.id), preReservationService.pendingPrereservationsByUsersAndEvent(users, event.id)) }
  }

  private def byId(id: ids.EventId) = EventsQ.where(_.id === id)

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
    Right(serviceSuccess(s"event.save.$key.success"))
  }

  private def validateEventPrice(pricing: Priced): Either[ServiceFailure, ServiceSuccess] = {
    pricing.price match {
      case price if price.amount < 0 => Left(serviceFailure("eventprice.save.positive"))
      case _ => Right(serviceSuccess("eventprice.save.success"))
    }
  }
}
