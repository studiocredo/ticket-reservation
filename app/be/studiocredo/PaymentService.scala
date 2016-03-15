package be.studiocredo


import models.ids.{OrderId, PaymentId}
import play.api.db.slick.Config.driver.simple._
import models.entities._
import scala.slick.session.Session
import models.schema.tables._
import com.google.inject.Inject
import be.studiocredo.util.ServiceReturnValues._
import models.Page
import scala.Some
import java.io.File
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import be.studiocredo.util.{AXATransactionImporter, Money}
import java.text.{DecimalFormatSymbols, DecimalFormat, SimpleDateFormat, DateFormat}
import org.joda.time.format.DateTimeFormat
import views.helper.PaymentRegisteredOption

class PaymentService @Inject()() {
  val PaymentsQ = Query(Payments)
  val PaymentsQActive = PaymentsQ.where(_.archived === false)
  val OrdersQ = Query(Orders)

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
      (query, filter) => query.filter(q => iLike(q.debtor, s"%${filter}%")) // should replace with lucene
    }

    val query = showAll match {
      case true => queryF
      case false => queryF.filter(q => q.archived === false)
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

  def insertTransactions(file: File)(implicit s: Session): List[PaymentId] = {
    val payments = new AXATransactionImporter().importFile(file)
    val known = (for {
      p <- PaymentsQ.filter(_.importId inSet payments.flatMap(_.importId))
    } yield p.importId).list
    payments.filterNot(pe => known.contains(pe.importId)).map(addOrderId).map(Payments.autoInc.insert)
  }

  private def addOrderId(payment: PaymentEdit)(implicit s: Session): PaymentEdit = {
    payment.message.fold(payment) { message =>
        OrderReference.parse(message).fold(payment) { orderReference =>
          OrdersQ.where(_.id === orderReference.order).exists.run match {
            case true => payment.copy(orderId = Some(orderReference.order))
            case false => payment
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

  def delete(id: PaymentId)(implicit s: Session) = {
    PaymentsQ.where(_.id === id).map(_.archived).update(true)
  }

  private def validatePaymentUpdate(id: PaymentId, data: PaymentEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    find(id) match {
      case None => Left(serviceFailure("payment.update.notfound"))
      case Some(payment) => {
        Right(serviceSuccess("payment.update.success"))
      }
    }
  }

  private def validatePayment(payment: PaymentEdit): Either[ServiceFailure, ServiceSuccess] = {
    Right(serviceSuccess("payment.insert.success"))
  }
}
