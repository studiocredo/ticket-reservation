package controllers

import akka.pattern.ask
import akka.util.Timeout
import be.studiocredo._
import be.studiocredo.auth.{SecuredDBRequest, _}
import be.studiocredo.reservations.FloorProtocol.Response
import be.studiocredo.reservations.{CapacityExceededException, FloorProtocol, MissingOrderException, ReservationEngineMonitorService}
import be.studiocredo.util.Money
import com.google.inject.Inject
import controllers.auth.Mailer
import models.HumanDateTime
import models.entities.FloorPlanJson._
import models.entities.SeatType.SeatType
import models.entities.{Identity, Order, SeatId, SeatType, _}
import models.ids._
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Controller, SimpleResult}

import scala.collection.immutable.Set
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


case class StartSeatOrderForm(quantity: Int, priceCategory: String, availableSeatTypes: List[SeatType])
case class OrderComments(comments: Option[String], keepUnusedPrereservations: Option[Boolean])
case class OrderBillingData(billingName: String, billingAddress: String)

class Orders @Inject()(ticketService: TicketService, eventService: EventService, orderService: OrderService, showService: ShowService, preReservationService: PreReservationService, venueService: VenueService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService, orderEngine: ReservationEngineMonitorService) extends Controller with Secure with UserContextSupport {
  val logger = Logger("be.studiocredo.orders")

  val defaultAuthorization = Some(Authorization.ANY)

  val startSeatOrderForm: Form[StartSeatOrderForm] = Form(
    mapping(
      "quantity" -> number(min = 0),
      "priceCategory" -> text(),
      "seatTypes" -> list(of[SeatType])
    )(StartSeatOrderForm.apply)(StartSeatOrderForm.unapply)
  )

  val orderCommentsForm: Form[OrderComments] = Form(
    mapping(
      "orderComments" -> optional(text),
      "keepUnusedPrereservations" -> optional(boolean)
    )(OrderComments.apply)(OrderComments.unapply)
  )

  val orderBillingDataForm: Form[OrderBillingData] = Form(
    mapping(
      "billingName" -> text(),
      "billingAddress" -> text()
    )(OrderBillingData.apply)(OrderBillingData.unapply)
  )

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Akka.system.dispatcher

  def start(id: EventId) = AuthDBAction { implicit rs =>
    val users = rs.currentUser match {
      case None => Nil
      case Some(identity) => identity.allUsers
    }
    eventService.eventReservationDetails(id, users) match {
      case Some(event) if !event.event.event.archived && (event.event.reservationAllowed || userContext.exists(_.reservationAllowed)) =>
        //assume that there is max only one unprocessed order per usergroup
        //if no order is found, create a new one
        val order = orderService.unprocessedOrdersByUsers(users).headOption.getOrElse(orderService.create(rs.currentUser.get.user))
        Redirect(controllers.routes.Orders.view(order, id))
      case _ => BadRequest(s"Evenement $id niet gevonden of reservaties niet toegelaten")
    }
  }

  def view(order: OrderId, event: EventId) = AuthDBAction { implicit rs =>
    ensureOrderAccess(order) {
      viewPage(order, event)
    }
  }

  def listActive() = AuthDBAction { implicit rs =>
    rs.currentUser.fold(BadRequest("Not logged in")) { user =>
      Ok(views.html.orders(orderService.orderPaymentsByUsers(user.allUsers, Some(activeOrderFilter)), userContext))
    }
  }

  private def activeOrderFilter(t: (TicketOrder, TicketSeatOrder, Show, Event, Venue)): Boolean = {
    !t._3.archived && !t._4.archived
  }

  def ticketDetails(reference: String) = AuthDBAction { implicit rs =>
    TicketDistribution.parse(reference) match {
      case None => BadRequest(views.html.ticket(reference, None, Map("error" -> s"Ongeldige referentie $reference"), userContext))
      case Some(ticket) => {
        ensureOrderAccess(ticket.order) {
          orderService.getWithPayments(ticket.order) match {
            case None => BadRequest(views.html.ticket(reference, None, Map("error" -> s"Bestelling ${ticket.order} niet gevonden"), userContext))
            case Some(order) => {
              ticketService.find(ticket.order) match {
                case Nil => BadRequest(views.html.ticket(reference, None, Map("error" -> s"Referentie ${reference} niet gevonden"), userContext))
                case last :: other => {
                  var messages = mutable.Map[String, String]()
                  if (last != ticket) {
                    if (!other.contains(ticket)) {
                      messages("warning") = s"Dit is niet het meest recente ticket. Er werd een nieuw ticket aangemaakt op ${HumanDateTime.formatDateCompact(last.date)} met referentie ${last.reference}"
                    } else {
                      messages("error") = s"Ongeldige referentie $reference"
                    }
                  }
                  if (!order.isPaid) {
                    messages("error") =  "Bestelling is nog niet (volledig) betaald"
                  }
                  Ok(views.html.ticket(reference, Some(order), messages.toMap, userContext))
                }
              }

            }
          }
        }
      }
    }
  }

  def confirm(order: OrderId, event: EventId) = AuthDBAction { implicit rs =>
    ensureOrderAccess(order) {
      val bindedForm = orderCommentsForm.bindFromRequest
      bindedForm.fold(
        formWithErrors => BadRequest(s"Bestelling $order niet afgesloten ${formWithErrors.errorsAsJson}"),
        comments => {
          val currentUser = rs.currentUser.get
          preReservationService.cleanupPrereservationsAndCloseOrder(order, comments.comments, event, currentUser.allUsers, comments.keepUnusedPrereservations.getOrElse(false)) match {
            case false => BadRequest(s"Bestelling $order niet afgesloten")
            case true => {
              val orderDetail = orderService.get(order).get
              if (orderDetail.order.comments.isDefined) {
                Mailer.sendOrderWithCommentsToAdmin(orderDetail)
              }
              Mailer.sendOrderConfirmationEmail(currentUser.user, orderDetail)
              Redirect(routes.Orders.overview(order))
            }
          }
        }
      )
    }
  }

  def updateBillingData(order: OrderId, event: EventId) = AuthDBAction { implicit rs =>
    ensureOrderAccess(order) {
      val bindedForm = orderBillingDataForm.bindFromRequest
      bindedForm.fold(
        formWithErrors => Redirect(controllers.routes.Orders.view(order, event)),
        billingData => {
          orderService.update(order, billingData.billingName, billingData.billingAddress)
          Redirect(controllers.routes.Orders.view(order, event))
        }
      )
    }
  }

  def overview(id: OrderId) = AuthDBAction { implicit rs =>
    ensureOrderAccess(id) {
      orderService.get(id) match {
        case None => BadRequest(s"Bestelling $id niet gevonden")
        case Some(order) => {
          Ok(views.html.orderOverview(order, userContext))
        }
      }
    }
  }
  
  def cancel(id: OrderId) = AuthDBAction { implicit rs =>
    import FloorProtocol._

    ensureOrderAccess(id) {
      orderService.destroy(id) match {
        case 0 => BadRequest(s"Bestelling $id niet gevonden")
        case _ => {
          (orderEngine.floors) ! ReloadFullState()
          Redirect(routes.Application.index())
        }
      }
    }
  }

  def startSeatOrder(id: ShowId, order: OrderId, event: EventId) = AuthDBAction.async {
    implicit rs =>
      import FloorProtocol._

      ensureOrderAccessAsync(order) {
        val bindedForm = startSeatOrderForm.bindFromRequest
        bindedForm.fold(
          formWithErrors => Future(startSeatOrderFailed(order, event)),
          res => {

            var validationError = false
            val priceCategoryKey = "event." + event.id + "." + res.priceCategory
            val money = Cache.getOrElse[Money](priceCategoryKey, 300) {
              val dbValue = eventService.getPricing(event, res.priceCategory)
              if (dbValue.isEmpty)
                validationError = true
              dbValue.getOrElse(Money(-1))
            }
            val avail = res.availableSeatTypes.toSet

            if (validationError) {
              Cache.remove(priceCategoryKey)
              Future(startSeatOrderFailed(order, event))
            } else {
              implicit val timeout = Timeout(30.seconds)

              (orderEngine.floors ? StartOrder(id, order, res.quantity, rs.user.allUsers, money, avail)).map {
                case status: Response => {
                  Redirect(routes.Orders.viewSeatOrder(id, order))
                }
              }.recover({
                case ooc: CapacityExceededException =>
                  logger.debug(s"$id $order: Capacity exceeded")
                  val remaining = ooc.remaining match {
                    case amount if amount <= 0 => "Er zijn geen vrije plaatsen"
                    case 1 => "Er is nog 1 plaats."
                    case other => s"Er zijn nog $other plaatsen"
                  }
                  startSeatOrderFailed(order, event, s"Onvoldoende plaatsen beschikbaar voor plaatskeuze. $remaining. Opgelet, niet opgenomen pre-reservaties blijven gewaarborgd, enkel de plaatskeuze is op dit moment niet mogelijk. Gelieve hiervoor later opnieuw te proberen.")
                case error =>
                  logger.error(s"$id $order: Failed to start seat order", error)
                  startSeatOrderFailed(order, event)
              })
            }
          }
        )
      }
  }

  private def startSeatOrderFailed(order: OrderId, event: EventId ,msg: String = "Er is een fout opgetreden bij het opstarten van de reservatie"): SimpleResult = {
    Redirect(routes.Orders.view(order, event)).flashing("start-order" -> msg)
  }

  def cancelSeatOrder(showId: ShowId, orderId: OrderId) = AuthDBAction.async {
    implicit rs =>
      import FloorProtocol._

      ensureOrderAccessAsync(orderId) {
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

      ensureOrderAccessAsync(orderId) {
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

  def ensureOrderAccess(orderId: OrderId)(action: => SimpleResult)(implicit rs: SecuredDBRequest[_]): SimpleResult = {
    Cache.getOrElse[Option[Order]]("order." + orderId.id, 300) {
      orderService.find(orderId)
    }.fold({
      logger.warn(s"$orderId: Order not found")
      BadRequest(s"Order $orderId niet gevonden")
    })(
        order => {
          if (rs.user.allUsers.contains(order.userId) || rs.user.roles.contains(Roles.Admin))
            action
          else {
            logger.warn(s"$order: Order for user ${order.userId} but ${rs.user.id} attempted to use it")
            BadRequest(s"Geen toegang tot order ${orderId}")
          }
        }
      )
  }


  def ensureOrderAccessAsync(orderId: OrderId)(action: => Future[SimpleResult])(implicit rs: SecuredDBRequest[_]): Future[SimpleResult] = {
    Cache.getOrElse[Option[Order]]("order." + orderId.id, 300) {
      orderService.find(orderId)
    }.fold({
      logger.warn(s"$orderId: Order not found")
      Future.successful(BadRequest(s"Order $orderId niet gevonden"))
    })(
          order => {
            if (order.userId == rs.user.id)
              action
            else {
              logger.warn(s"$order: Order for user ${order.userId} but ${rs.user.id} attempted to use it")
              Future.successful(BadRequest(s"Geen toegang tot order $order"))
            }
          }
        )
  }

  def viewSeatOrder(id: ShowId, order: OrderId) = AuthDBAction.async { implicit rs =>
    ensureOrderAccessAsync(order) {
      Future.successful(Ok(views.html.reservationFloorplan(showService.getEventShow(id), order, userContext)))
    }
  }

 def cancelTicketOrder(order: OrderId, event: EventId, ticket: TicketOrderId) = AuthDBAction { implicit rs =>
   import FloorProtocol._

   ensureOrderAccess(order) {
     orderService.destroyTicketOrders(ticket) match {
       case 0 => BadRequest(s"Bestelling $ticket niet gevonden")
       case _ => {
         (orderEngine.floors) ! ReloadFullState()
         Redirect(routes.Orders.view(order, event))
       }
     }
   }
  }


  def toJson(status: Response) = Json.obj("plan" -> Json.toJson(status.floorPlan), "timeout" -> status.timeout, "seq" -> status.seq)

  def ajaxFloorplan(id: ShowId, order: OrderId) = AuthDBAction.async { implicit rs =>
    import FloorProtocol._

    ensureOrderAccessAsync(order) {

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

    ensureOrderAccessAsync(order) {

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

    ensureOrderAccessAsync(order) {

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

  def reloadStatus() = AuthDBAction(Authorization.ADMIN) { implicit rs =>
    import FloorProtocol._

    (orderEngine.floors) ! ReloadFullState()
    Redirect(routes.Application.index()).flashing("success" -> "Status herladen")

  }


  private def viewPage(id: OrderId, eventId: EventId, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.allUsers
    }
    eventService.eventReservationDetails(eventId, users) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(event) =>
        if (event.event.event.archived) {
          BadRequest(s"Evenement $id niet beschikbaar")
        } else {
          orderService.get(id) match {
            case None => BadRequest(s"Bestelling $id niet gevonden")
            case Some(order) if !order.order.processed =>
              status(views.html.order(event, order, getSeatTypes(rs.user), userContext))
            case _ => BadRequest(s"Bestelling $id is afgesloten")
          }
        }
    }
  }

  private def getSeatTypes(user: Identity): Set[SeatType] = {
    if (Authorization.ADMIN.isAuthorized(user)) {
      SeatType.values
    } else {
      current.configuration.getBoolean("application.disable-accessible-seats").getOrElse(false) match {
        case true => current.configuration.getBoolean("application.disable-vip-seats").getOrElse(false) match {
          case true => SeatType.values
          case false => Set(SeatType.Normal, SeatType.Disabled)
        }
        case false => current.configuration.getBoolean("application.disable-vip-seats").getOrElse(false) match {
          case true => Set(SeatType.Normal, SeatType.Vip)
          case false => Set(SeatType.Normal)
        }
      }
    }
  }
}
