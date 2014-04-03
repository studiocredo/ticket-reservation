package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth._
import play.api.mvc.Controller
import models.ids._
import models.entities.FloorPlanJson._
import play.api.data.Form
import play.api.data.Forms._
import controllers.auth.Mailer
import scala.Some
import play.api.libs.json.{JsError, Json}
import be.studiocredo.reservations.{MissingOrderException, FloorProtocol, ReservationEngineMonitorService}
import scala.collection.immutable.Set
import be.studiocredo.util.Money
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import play.api.Play.current
import scala.concurrent.Future
import be.studiocredo.reservations.FloorProtocol.StartOrder
import play.api.Logger
import play.api.cache.Cache
import models.entities.SeatType.SeatType
import models.entities.SeatType
import models.entities.SeatId
import be.studiocredo.reservations.FloorProtocol.Response
import play.api.mvc.SimpleResult
import models.entities.Identity
import models.entities.Order
import be.studiocredo.reservations.CapacityExceededException
import be.studiocredo.auth.SecuredDBRequest


case class StartSeatOrderForm(quantity: Int, priceCategory: String, availableSeatTypes: List[SeatType])

class Orders @Inject()(eventService: EventService, orderService: OrderService, showService: ShowService, preReservationService: PreReservationService, venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService, orderEngine: ReservationEngineMonitorService) extends Controller with Secure with UserContextSupport {
  val logger = Logger("be.studiocredo.orders")

  val defaultAuthorization = Some(Authorization.ANY)

  val startSeatOrderForm: Form[StartSeatOrderForm] = Form(
    mapping(
      "quantity" -> number(min = 0),
      "priceCategory" -> text(),
      "seatTypes" -> list(of[SeatType])
    )(StartSeatOrderForm.apply)(StartSeatOrderForm.unapply)
  )

  implicit val ec = play.api.libs.concurrent.Akka.system.dispatcher

  def start(id: EventId) = AuthDBAction { implicit rs =>
    val users = rs.currentUser match {
      case None => Nil
      case Some(identity) => identity.allUsers
    }
    eventService.eventReservationDetails(id, users) match {
      case Some(event) if event.event.reservationAllowed => {
        //assume that there is max only one unprocessed order per usergroup
        //if no order is found, create a new one
        val order = orderService.unprocessedOrdersByUsers(users).headOption.getOrElse(orderService.create(rs.currentUser.get.user))
        Redirect(controllers.routes.Orders.view(order, id))
      }
      case _ => BadRequest(s"Evenement $id niet gevonden of reservaties niet toegelaten")
    }
  }

  def view(order: OrderId, event: EventId) = AuthDBAction { implicit rs =>
    viewPage(order, event)
  }

  def confirm(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order)  => {
        val currentUser = rs.currentUser.get
        orderService.close(id)
        Mailer.sendOrderConfirmationEmail(currentUser.user, order)
        Redirect(routes.Orders.overview(id))
      }
    }
  }

  def overview(id: OrderId) = AuthDBAction { implicit rs =>
    orderService.get(id) match {
      case None => BadRequest(s"Bestelling $id niet gevonden")
      case Some(order)  => {
        Ok(views.html.orderOverview(order, userContext))
      }
    }
  }
  
  def cancel(id: OrderId) = AuthDBAction { implicit rs =>
    import FloorProtocol._

    orderService.destroy(id) match {
      case 0 => BadRequest(s"Bestelling $id niet gevonden")
      case _ => {
        (orderEngine.floors) ! ReloadState
        Redirect(routes.Application.index())
      }
    }
  }

  def startSeatOrder(id: ShowId, order: OrderId, event: EventId) = AuthDBAction.async {
    implicit rs =>
      import FloorProtocol._

      ensureOrderAccess(id, order) {
        val bindedForm = startSeatOrderForm.bindFromRequest
        bindedForm.fold(
          formWithErrors => Future.successful(Redirect(routes.Orders.view(order, event))),
          res => {

            val money = Cache.getOrElse[Money]("event." + event.id + "." + res.priceCategory, 300) {
              eventService.getPricing(event, res.priceCategory).get
            } //TODO error handling if not found
            val avail = res.availableSeatTypes.toSet

            implicit val timeout = Timeout(30.seconds)

            (orderEngine.floors ? StartOrder(id, order, res.quantity, rs.user.allUsers, money, avail)).map {
              case status: Response => {

                Redirect(routes.Orders.viewSeatOrder(id, order))
              }
            }.recover({
              case ooc: CapacityExceededException =>
                logger.debug(s"$id $order: Capacity exceeded")
                InternalServerError("No room sorry :(")
              case error =>
                logger.error(s"$id $order: Failed to start seat order", error)
                InternalServerError
            })
          }
        )
      }
  }

  def cancelSeatOrder(showId: ShowId, orderId: OrderId) = AuthDBAction.async {
    implicit rs =>
      import FloorProtocol._

      ensureOrderAccess(showId, orderId) {
        implicit val timeout = Timeout(30.seconds)

        (orderEngine.floors ? Cancel(showId, orderId)).map {
          case status: Response => {

            Redirect(routes.Orders.view(orderId, toEventId(showId)))
          }
        }.recover({
          case error =>
            logger.error(s"$showId $orderId: Failed to cancel order", error)
            InternalServerError
        })

      }
  }

  def commitSeatOrder(showId: ShowId, orderId: OrderId) = AuthDBAction.async {
    implicit rs =>
      import FloorProtocol._

      ensureOrderAccess(showId, orderId) {
        implicit val timeout = Timeout(30.seconds)

        (orderEngine.floors ? Commit(showId, orderId)).map {
          case status: Response => {

            Redirect(routes.Orders.view(orderId, toEventId(showId)))
          }
        }.recover({
          case error =>
            logger.error(s"$showId $orderId: Failed to commit order", error)
            InternalServerError
        })

      }
  }

  def toEventId(showId: ShowId)(implicit rs: SecuredDBRequest[_]): EventId = {
    Cache.getOrElse[EventId]("show2event." + showId.id, 60 * 60) {
      showService.get(showId).get.eventId
    }
  }

  def ensureOrderAccess(showID: ShowId, orderId: OrderId)(action: => Future[SimpleResult])(implicit rs: SecuredDBRequest[_]): Future[SimpleResult] = {
    Cache.getOrElse[Option[Order]]("order." + orderId.id, 300) {
      orderService.find(orderId)
    }.fold({
      logger.warn(s"$showID $orderId: Order not found")
      Future.successful(BadRequest(s"Order $orderId niet gevonden"))
    })(
          order => {
            if (order.userId == rs.user.id)
              action
            else {
              logger.warn(s"$showID $order: Order for user ${order.userId} but ${rs.user.id} attempted to use it")
              Future.successful(BadRequest(s"Geen toegang tot order $order"))
            }
          }
        )
  }



  def viewSeatOrder(id: ShowId, order: OrderId) = AuthDBAction.async { implicit rs =>
    ensureOrderAccess(id, order) {
      Future.successful(Ok(views.html.reservationFloorplan(showService.getEventShow(id), order, userContext)))
    }
  }

 def cancelTicketOrder(id: TicketOrderId) = AuthDBAction { implicit rs =>
   import FloorProtocol._

   orderService.destroyTicketOrders(id) match {
     case 0 => BadRequest(s"Bestelling $id niet gevonden")
     case _ => {
       (orderEngine.floors) ! ReloadState
       Redirect(routes.Application.index())         //TODO redirect to same page (need order id and event id)
     }
   }
  }


  def toJson(status: Response) = Json.obj("plan" -> Json.toJson(status.floorPlan), "timeout" -> status.timeout, "seq" -> status.seq)

  def ajaxFloorplan(id: ShowId, order: OrderId) = AuthDBAction.async { implicit rs =>
    import FloorProtocol._

    ensureOrderAccess(id, order) {

      implicit val timeout = Timeout(30.seconds)

      (orderEngine.floors ? CurrentStatus(id, order)).map {
        case status: Response => { Ok(toJson(status)) }
      }.recover({
        case error: MissingOrderException => {
          NotFound(Json.obj("error" -> "missing", "redirect" -> controllers.routes.Orders.view(order, toEventId(id)).url))
        }
        case error => {
          logger.warn(s"$id $order: Failed to retrieve  floorplan", error)
          InternalServerError
        }
      })
    }
  }

  case class AjaxMove(target: SeatId, seats: Option[List[SeatId]])
  implicit val ajaxMoveFMT = Json.format[AjaxMove]

  def ajaxMove(id: ShowId, order: OrderId) = AuthDBAction.async(parse.json) { implicit rs =>
    import FloorProtocol._

    ensureOrderAccess(id, order) {

      implicit val timeout = Timeout(5.seconds)

      rs.body.validate[AjaxMove].map {
        case (move) => {
          (orderEngine.floors ? Move(id, order, move.target, move.seats.map(_.toSet))).map {
            case status: Response => Ok(toJson(status))
          }.recover({
            case error => {
              logger.warn(s"$id $order: Failed to move to seat $move", error)
              InternalServerError
            }
          })
        }
      }.recoverTotal {
        e => Future.successful(BadRequest("Detected error:" + JsError.toFlatJson(e)))
      }
    }
  }


  def ajaxMoveBest(id: ShowId, order: OrderId) = AuthDBAction.async { implicit rs =>
    import FloorProtocol._

    ensureOrderAccess(id, order) {

      implicit val timeout = Timeout(30.seconds)

      (orderEngine.floors ? MoveBest(id, order)).map {
        case status: Response => Ok(toJson(status))
      }.recover({
        case error => {
          logger.warn(s"Failed to retreive ajax floorplan $id $order", error)
          InternalServerError
        }
      })
    }
  }


  private def viewPage(id: OrderId, event: EventId, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.allUsers
    }
    eventService.eventReservationDetails(event, users) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(event) => {
        orderService.get(id) match {
          case None => BadRequest(s"Bestelling $id niet gevonden")
          case Some(order) if !order.order.processed => {
            status(views.html.order(event, order, getSeatTypes(rs.user), userContext))
          }
          case _ => BadRequest(s"Bestelling $id is afgesloten")
        }
      }
    }
  }

  private def getSeatTypes(user: Identity): Set[SeatType] = {
    if (Authorization.ADMIN.isAuthorized(user)) {
      SeatType.values
    } else {
      Set(SeatType.Normal)
    }
  }
}
