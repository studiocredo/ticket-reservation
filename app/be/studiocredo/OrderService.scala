package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models._
import models.entities._
import models.ids._
import scala.collection.mutable
import models.entities.SeatType._
import scala.Some
import com.google.inject.Inject
import be.studiocredo.util.ServiceReturnValues._
import models.admin.RichUser
import com.github.tototoshi.slick.JodaSupport._
import org.joda.time.DateTime

class OrderService @Inject()(venueService: VenueService) {
  import models.schema.tables._

  val OQ = Query(Orders)
  val TOQ = Query(TicketOrders)
  val TSOQ = Query(TicketSeatOrders)

  def byShowId(id: ShowId, excludedUsers: List[UserId] = List())(implicit s: Session): List[TicketSeatOrder] = {
    val query = for {
      tso <- TSOQ if tso.showId === id
    } yield (tso)
    query.list.filter{ tso =>
      tso.userId match {
        case Some(userId) => !excludedUsers.contains(userId)
        case None => true
      }
    }
  }

  //returns orders that only contain the ticketseatorders for a specific show
  def detailsByShowId(showId: ShowId)(implicit s: Session): List[OrderDetail] = {
    val orderIdsQuery = for {
      ticketOrder <- TOQ
      ticketSeatOrder <- TSOQ
      if ticketSeatOrder.ticketOrderId === ticketOrder.id
      if ticketSeatOrder.showId === showId
    } yield (ticketOrder.orderId)
    val orderIdsForShow = orderIdsQuery.list.distinct
    
    val ordersQuery = for {
      order <- OQ
      if order.id inSet orderIdsForShow
      user <- Users
      if user.id === order.userId
    } yield(order, user)

    val ordersUsers = ordersQuery.sortBy(_._1.date).list
    val orders = ordersUsers.map(_._1)

    val ticketOrderQuery = for {
      ticketOrder <- TicketOrders if ticketOrder.orderId inSet orderIdsForShow
      ticketSeatOrder <- TicketSeatOrders if ticketSeatOrder.ticketOrderId === ticketOrder.id
      if ticketSeatOrder.showId === showId
      show <- Shows if ticketOrder.showId === show.id
      event <- Events if show.eventId === event.id
      venue <- Venues if show.venueId === venue.id
    } yield (ticketOrder, ticketSeatOrder, show, event, venue)

    val orderMap = new mutable.HashMap[OrderId, mutable.Set[TicketOrder]]() with mutable.MultiMap[OrderId, TicketOrder]
    val ticketOrderMap = new mutable.HashMap[TicketOrder, mutable.Set[TicketSeatOrder]]() with mutable.MultiMap[TicketOrder, TicketSeatOrder]
    val showMap = mutable.Map[ShowId, EventShow]()
    val userMap = mutable.Map[UserId, User]()

    ordersUsers.map(_._2).foreach( u => userMap.put(u.id, u))

    val x = ticketOrderQuery.list
    x.foreach { case (ticketOrder, ticketSeatOrder, show, event, venue) =>
      orderMap.addBinding(ticketOrder.orderId, ticketOrder)
      ticketOrderMap.addBinding(ticketOrder, ticketSeatOrder)
      showMap.put(show.id, EventShow(show.id, event.id, event.name, show.venueId, venue.name, show.date, show.archived))
    }

    orders.map { order =>
      val ticketOrders = orderMap.get(order.id).getOrElse(Set.empty).toList.map{ ticketOrder =>
        val ticketSeatOrders = ticketOrderMap(ticketOrder).toList.map { ticketSeatOrder => TicketSeatOrderDetail(ticketSeatOrder, showMap(ticketSeatOrder.showId)) }
        TicketOrderDetail(ticketOrder, order, showMap(ticketOrder.showId), ticketSeatOrders)
      }
      OrderDetail(order, userMap(order.userId), ticketOrders)
    }.toList
  }

  private def orderDetail(implicit s: Session): (Order) => OrderDetail = {
    (order) => {
      val ticketOrderDetailsQuery = for {
        ticketOrder <- TicketOrders if ticketOrder.orderId === order.id
        ticketSeatOrder <- TicketSeatOrders if ticketSeatOrder.ticketOrderId === ticketOrder.id
        show <- Shows if ticketOrder.showId === show.id
        event <- Events if show.eventId === event.id
        venue <- Venues if show.venueId === venue.id
      } yield (ticketOrder, ticketSeatOrder, show, event, venue)

      val ticketOrderMap = new mutable.HashMap[TicketOrder, mutable.Set[TicketSeatOrder]]() with mutable.MultiMap[TicketOrder, TicketSeatOrder]
      val showMap = mutable.Map[ShowId, EventShow]()

      val orderUserQuery = for {
        dborder <- OQ if dborder.id === order.id
        user <- Users if dborder.userId === user.id
      } yield (user)
      val orderUser = orderUserQuery.first

      ticketOrderDetailsQuery.list.foreach {
        case (ticketOrder, ticketSeatOrder, show, event, venue) =>
          ticketOrderMap.addBinding(ticketOrder, ticketSeatOrder)
          showMap.put(show.id, EventShow(show.id, event.id, event.name, show.venueId, venue.name, show.date, show.archived))
      }


      val ticketOrders = ticketOrderMap.keys.map {
        ticketOrder =>
          val ticketSeatOrders = ticketOrderMap(ticketOrder).toList.map {
            ticketSeatOrder => TicketSeatOrderDetail(ticketSeatOrder, showMap(ticketSeatOrder.showId))
          }
          TicketOrderDetail(ticketOrder, order, showMap(ticketOrder.showId), ticketSeatOrders)
      }
      OrderDetail(order, orderUser, ticketOrders.toList)
    }
  }

  def find(id: OrderId)(implicit  s: Session): Option[Order] = {
    val q = for {
      order <- OQ if (order.id === id)
    } yield (order)
    q.firstOption
  }


  def get(id: OrderId)(implicit  s: Session): Option[OrderDetail] = {
    val q = for {
      order <- OQ
      if (order.id === id)
    } yield (order)
    q.firstOption.map(orderDetail)
  }

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[OrderDetail] = {
    import models.queries._

    val offset = pageSize * page

    val query = filter.foldLeft(OQ.sortBy(_.date.desc)){
      (query, filter) => query.filter(q => iLike(q.billingName, s"%${filter}%")) // should replace with lucene
    }
    val total = query.length.run
    val values = paginate(query, page, pageSize).run map orderDetail
    Page(values, page, pageSize, offset, total)
  }

  def prereservationSeatsByUser(id: UserId)(implicit s: Session): List[TicketSeatOrderDetail] = {
    prereservationSeatsByUsers(List(id))
  }

  def prereservationSeatsByUsers(ids: List[UserId])(implicit s: Session): List[TicketSeatOrderDetail] = {
    val query = for {
      (((ticketSeatOrder, show), event), venue) <- TicketSeatOrders.leftJoin(Shows).on(_.showId === _.id).leftJoin(Events).on(_._2.eventId === _.id).leftJoin(Venues).on(_._1._2.venueId === _.id)
      if ticketSeatOrder.userId inSet ids
    } yield (ticketSeatOrder, show, event, venue)
    query.list.map{ case (tso: TicketSeatOrder, s: Show, e: Event, v: Venue) => TicketSeatOrderDetail(tso, EventShow(s.id, e.id, e.name, s.venueId, v.name, s.date, s.archived)) }
  }
  
  def detailedOrdersByUser(id: UserId)(implicit s: Session): List[OrderDetail] = {
    detailedOrdersByUsers(List(id))
  }

  //http://stackoverflow.com/questions/18147396/comparing-type-mapped-values-in-slick-queries
  //TODO
  def seatOrderExists(show: ShowId, seat: SeatId)(implicit s: Session): Boolean = {
    val query = for {
      tso <- TSOQ
      if tso.showId === show
//      if tso.seat === (seat:SeatId)  <- THIS DOESNT WORK
    } yield tso
    query.list.filter(tso => tso.seat == seat).headOption.isDefined
  }

  def unprocessedOrdersByUsers(ids: List[UserId])(implicit s: Session): List[OrderId] = {
    val q = for {
      order <- OQ
      if order.processed === false
      if order.userId inSet ids
    } yield (order.id)
    q.list
  }

  def detailedOrdersByUsers(ids: List[UserId])(implicit s: Session): List[OrderDetail] = {
    detailedOrdersByUsersInternal(OQ, ids)
  }

  private def detailedOrdersByUsersInternal(startQuery: Query[schema.Orders, Order], ids: List[UserId])(implicit s: Session): List[OrderDetail] = {
    val ordersQuery = for {
      order <- startQuery
      if order.userId inSet ids
      user <- Users
      if user.id === order.userId
    } yield(order, user)

    val ordersUsers = ordersQuery.sortBy(_._1.date).list
    val orders = ordersUsers.map(_._1)

   val ticketOrderQuery = for {
      ticketOrder <- TicketOrders if ticketOrder.orderId inSet orders.map(_.id)
      ticketSeatOrder <- TicketSeatOrders if ticketSeatOrder.ticketOrderId === ticketOrder.id
      show <- Shows if ticketOrder.showId === show.id
      event <- Events if show.eventId === event.id
      venue <- Venues if show.venueId === venue.id
    } yield (ticketOrder, ticketSeatOrder, show, event, venue)

    val orderMap = new mutable.HashMap[OrderId, mutable.Set[TicketOrder]]() with mutable.MultiMap[OrderId, TicketOrder]
    val ticketOrderMap = new mutable.HashMap[TicketOrder, mutable.Set[TicketSeatOrder]]() with mutable.MultiMap[TicketOrder, TicketSeatOrder]
    val showMap = mutable.Map[ShowId, EventShow]()
    val userMap = mutable.Map[UserId, User]()

    ordersUsers.map(_._2).foreach( u => userMap.put(u.id, u))

    val x = ticketOrderQuery.list
    x.foreach { case (ticketOrder, ticketSeatOrder, show, event, venue) =>
      orderMap.addBinding(ticketOrder.orderId, ticketOrder)
      ticketOrderMap.addBinding(ticketOrder, ticketSeatOrder)
      showMap.put(show.id, EventShow(show.id, event.id, event.name, show.venueId, venue.name, show.date, show.archived))
    }

    orders.map { order =>
      val ticketOrders = orderMap.get(order.id).getOrElse(Set.empty).toList.map{ ticketOrder =>
        val ticketSeatOrders = ticketOrderMap(ticketOrder).toList.map { ticketSeatOrder => TicketSeatOrderDetail(ticketSeatOrder, showMap(ticketSeatOrder.showId)) }
        TicketOrderDetail(ticketOrder, order, showMap(ticketOrder.showId), ticketSeatOrders)
      }
      OrderDetail(order, userMap(order.userId), ticketOrders)
    }.toList
  }

  def createDetailed(user: RichUser)(implicit s:Session): OrderDetail = get(create(user)).get
  def create(user: RichUser)(implicit s:Session): OrderId = insert(OrderEdit(user.id, org.joda.time.DateTime.now, user.name, user.address.getOrElse("n/a"), false, None))
  def insert(order: OrderEdit)(implicit s: Session): OrderId = Orders.autoInc.insert(order)
  def update(id: OrderId, billingName: String, billingAddress: String)(implicit s: Session) = byId(id).map(_.billingEdit).update((billingName, billingAddress))

  def insert(orderId: OrderId, showId: ShowId)(implicit s: Session): TicketOrderId = TicketOrders.autoInc.insert((orderId, showId))

  def insert(seatOrders: List[TicketSeatOrder])(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    seatOrders.map{_.seat}
    val vQuery = for {
      (show, venue) <- Shows.leftJoin(Venues).on(_.venueId === _.id)
      if show.id inSet seatOrders.map{_.showId}
    } yield (show.id, venue)
    val venueMap = vQuery.list.toMap

    val badSeat = seatOrders.view.find {
      case so =>
        venueMap(so.showId).floorplan match {
          case Some(floorplan) => floorplan.seat(so.seat).isEmpty
          case None => true
        }
    }
    badSeat match {
      case Some(seatOrder) => Left(serviceFailure("reservations.seat.unknown", List(seatOrder.seat.name, venueMap(seatOrder.showId).name)))
      case None => {
        seatOrders.find { case so => seatOrderExists(so.showId, so.seat) } match {
          case Some(seatOrder) => Left(serviceFailure("reservation.seat.reserved", List(seatOrder.seat.name)))
          case None => {
            seatOrders.foreach { TicketSeatOrders.*.insert }
            Right(serviceSuccess("reservations.success"))
          }
        }
      }
    }
  }

  def close(id: OrderId, comments: Option[String] = None)(implicit s: Session): Boolean = {
    s.withTransaction {
      (for {
        o <- OQ if o.id === id
      } yield o.processed ~ o.comments).update((true, comments)) == 1
    }
  }

  def closeStale()(implicit s: Session): List[OrderDetail] = {
    val q = OQ.where(_.processed === false).where(_.date < DateTime.now().minusHours(6)) //TODO make configurable
    val (emptyOrders, nonEmptyOrders) = q.list.map(orderDetail).partition(order => order.ticketSeatOrders.isEmpty)
    q.map(_.processed).update(true)
    emptyOrders.foreach { emptyOrder =>
      destroy(emptyOrder.id)
    }
    nonEmptyOrders
  }

  def destroy(id: OrderId)(implicit s: Session) = {
    s.withTransaction {
      val q = for {
        to <- TOQ
        if to.orderId === id
      } yield (to.id)
      val ids = q.list
      TSOQ.where(_.ticketOrderId inSet ids).delete
      TOQ.where(_.orderId === id).delete
      OQ.where(_.id === id).delete
    }
  }

  def destroyTicketOrders(id: TicketOrderId)(implicit s: Session) = {
    s.withTransaction {
      TSOQ.where(_.ticketOrderId === id).delete
      TOQ.where(_.id === id).delete
    }
  }

  private def byId(id: ids.OrderId)= OQ.where(_.id === id)
}
