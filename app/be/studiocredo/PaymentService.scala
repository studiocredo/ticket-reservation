package be.studiocredo


import java.io.File

import be.studiocredo.account.AXATransactionImporter
import be.studiocredo.util.ServiceReturnValues._
import com.google.inject.Inject
import models.entities._
import models.ids.{OrderId, PaymentId}
import models.schema.tables._
import models.{Page, schema}
import play.api.db.slick.Config.driver.simple._
import views.helper.PaymentRegisteredOption

import scala.slick.session.Session

class PaymentService @Inject()() {
  val PaymentsQ: Query[schema.Payments, Payment] = Query(Payments)
  val PaymentsQActive: Query[schema.Payments, Payment] = PaymentsQ.where(_.archived === false)
  val OrdersQ: Query[schema.Orders, Order] = Query(Orders)

  def page(page: Int = 0, showAll: Boolean, pageSize: Int = 10, orderBy: Int = 1, nameFilter: Option[String] = None, registeredFilter: PaymentRegisteredOption.Option = PaymentRegisteredOption.default)(implicit s: Session): Page[Payment] = {
    import models.queries._

    val offset = pageSize * page

    val baseQuery = PaymentsQ.sortBy(r => (r.date.desc, r.id.desc))
    val registeredFilterQuery = registeredFilter match {
      case PaymentRegisteredOption.Registered => baseQuery.where(_.orderId.isNotNull)
      case PaymentRegisteredOption.Unregistered => baseQuery.where(_.orderId.isNull)
      case PaymentRegisteredOption.Both => baseQuery
      case _ => baseQuery
    }

    val queryF = nameFilter.foldLeft(registeredFilterQuery){
      (query, filter) => query.filter(q => iLike(q.debtor, s"%$filter%")) // should replace with lucene
    }

    val query = if (showAll) {
      queryF
    } else {
      queryF.filter(q => q.archived === false)
    }

    val total = query.length.run
    val values = paginate(query, page, pageSize).run
    Page(values, page, pageSize, offset, total)
  }

  def find(id: PaymentId)(implicit s: Session): Option[Payment] = {
    PaymentsQ.filter(q => q.id === id).firstOption
  }

  def find(id: OrderId)(implicit s: Session): List[Payment] = {
    PaymentsQ.filter(q => q.orderId === id).list
  }

  def insert(payment: PaymentEdit)(implicit s: Session): Either[ServiceFailure, PaymentId] = {
    validatePayment(payment).fold(
      error => Left(error),
      success => Right(Payments.autoInc.insert(payment))
    )
  }

  def upload(payments: Seq[PaymentEdit])(implicit s: Session): Seq[PaymentId] = {
    val known = (for {
      p <- PaymentsQ.filter(_.importId inSet payments.flatMap(_.importId))
    } yield p.importId).list
    payments.filterNot(pe => known.contains(pe.importId)).map(addOrderId).map(Payments.autoInc.insert)
  }

  private def addOrderId(payment: PaymentEdit)(implicit s: Session): PaymentEdit = {
    payment.message.fold(payment) { message =>
        OrderReference.parse(message).fold(payment) { orderReference =>
          if (OrdersQ.where(_.id === orderReference.order).where(_.userId === orderReference.user).where(_.archived === false).exists.run) {
            payment.copy(orderId = Some(orderReference.order))
          } else {
            payment
          }
        }
    }
  }

  def getEdit(id: PaymentId)(implicit s: Session): Option[PaymentEdit] = {
    find(id) map {pe => PaymentEdit(pe.paymentType, pe.importId, pe.orderId, pe.debtor, pe.amount, pe.message, pe.details, pe.date, pe.archived)}
  }

  def update(id: PaymentId, data: PaymentEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validatePaymentUpdate(id, data).fold(
      failure => Left(failure),
      success => {
        val paymentUpdate = for {
          p <- PaymentsQ.filter(_.id === id)
        } yield p.paymentType ~ p.importId ~ p.orderId ~ p.debtor ~ p.amount ~ p.message ~ p.details ~ p.date ~ p.archived

        paymentUpdate.update(PaymentEdit.unapply(data).get)

        Right(serviceSuccess("payment.update.success"))
      })
  }

  def delete(id: PaymentId)(implicit s: Session): Int = {
    PaymentsQ.where(_.id === id).map(_.archived).update(true)
  }

  private def validatePaymentUpdate(id: PaymentId, data: PaymentEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    find(id) match {
      case None => Left(serviceFailure("payment.update.notfound"))
      case Some(payment) =>
        Right(serviceSuccess("payment.update.success"))
    }
  }

  private def validatePayment(payment: PaymentEdit): Either[ServiceFailure, ServiceSuccess] = {
    Right(serviceSuccess("payment.insert.success"))
  }
}
