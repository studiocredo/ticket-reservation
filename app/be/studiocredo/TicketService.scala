package be.studiocredo

import be.studiocredo.reservations.TicketGenerator
import be.studiocredo.util.ServiceReturnValues._
import com.github.tototoshi.slick.JodaSupport._
import com.google.inject.Inject
import models.entities._
import models.ids.OrderId
import models.schema.tables._
import org.joda.time.DateTime
import play.api.db.slick.Config.driver.simple._

import scala.slick.session.Session

class TicketService @Inject()(orderService: OrderService) {
  val TicketQ = Query(TicketDistributionLog)

  def find(id: OrderId)(implicit s: Session): List[TicketDistribution] = {
    TicketQ.filter(q => q.orderId === id).sortBy(_.serial.desc).list
  }

  def find(id: OrderId, serial: Int)(implicit s: Session): Option[TicketDistribution] = {
    TicketQ.filter(q => q.orderId === id && q.serial === serial).firstOption
  }

  def find(ticket: TicketDistribution)(implicit s: Session): Option[TicketDistribution] = {
    TicketQ.filter(q => q.orderId === ticket.order && q.serial === ticket.serial && q.date === ticket.date).firstOption
  }

  def findOrCreate(order: OrderId)(implicit s: Session): Either[ServiceFailure, TicketDistribution] = {
    find(order).headOption.fold(create(order))(Right(_))
  }

  def create(orderId: OrderId)(implicit s: Session): Either[ServiceFailure, TicketDistribution] = {
    orderService.find(orderId) match {
      case None => Left(serviceFailure("ticket.order.notfound"))
      case Some(order) =>
        val ticket = TicketDistribution(order.id, nextSerial(order.id), DateTime.now)
        TicketDistributionLog.*.insert(ticket)
        Right(ticket)
    }
  }

  def createForNew()(implicit s: Session): List[TicketDistribution] = {
    val now = DateTime.now
    val paidOrders = orderService.findPaidForUpcomingShows()
    val processedOrders = ( for {
      ticket <- TicketQ
      if ticket.orderId inSet paidOrders
    } yield ticket.orderId ).list

    (paidOrders.toSet -- processedOrders.toSet).map { order =>
      val ticket = TicketDistribution(order, nextSerial(order), now)
      TicketDistributionLog.*.insert(ticket)
      ticket
    }.toList
  }

  def generate(ticket: TicketDistribution, url: String)(implicit s: Session): Either[ServiceFailure, TicketDocument] = {
    find(ticket.order, ticket.serial) match {
      case None => Left(serviceFailure("ticket.notfound"))
      case Some(actualTicket) =>
        orderService.get(actualTicket.order) match {
          case None => Left(serviceFailure("ticket.order.notfound"))
          case Some(order) =>
            TicketGenerator.create(order, actualTicket, url) match {
              case None => Left(serviceFailure("ticket.generation.failed"))
              case Some(ticketDocument) => Right(ticketDocument)
            }
        }
    }
  }

  private def nextSerial(id: OrderId)(implicit s: Session): Int = {
    Query(TicketDistributionLog.where(_.orderId === id).map(_.serial).max).first.getOrElse(0) + 1
  }
}
