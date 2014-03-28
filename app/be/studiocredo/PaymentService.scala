package be.studiocredo


import models.ids.{OrderId, PaymentId, UserId}
import play.api.db.slick.Config.driver.simple._
import models.entities._
import scala.slick.session.Session
import models.schema.tables._
import com.google.inject.Inject
import be.studiocredo.auth.{Passwords, Password, Roles}
import models.admin._
import models.Page
import be.studiocredo.util.ServiceReturnValues._
import models.admin.RichUser
import models.entities.UserDetailEdit
import models.entities.UserDetail
import models.Page
import scala.Some
import models.entities.User
import models.entities.UserEdit
import be.studiocredo.auth.Password
import models.entities.UserRole
import models.admin.UserFormData

class PaymentService @Inject()() {
  val PaymentsQ = Query(Payments)

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[Payment] = {
    import models.queries._

    val offset = pageSize * page
    val query = filter.foldLeft(PaymentsQ.sortBy(_.date.desc)){
      (query, filter) => query.filter(q => iLike(q.debtor, s"%${filter}%")) // should replace with lucene
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

  def insert(payment: PaymentEdit)(implicit s: Session): PaymentId = {
    Payments.autoInc.insert(payment)
  }

  def getEdit(id: PaymentId)(implicit s: Session): Option[PaymentEdit] = {
    find(id) map {pe => PaymentEdit(pe.orderId, pe.debtor, pe.amount, pe.details, pe.date)}
  }

  def update(id: PaymentId, data: PaymentEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validatePaymentUpdate(id, data).fold(
      failure => Left(failure),
      success => {
        val paymentUpdate = for {
          p <- PaymentsQ.filter(_.id === id)
        } yield p.orderId ~ p.debtor ~ p.amount ~ p.details ~ p.date

        paymentUpdate.update(PaymentEdit.unapply(data).get)

        Right(serviceSuccess("payment.update.success"))
      })
  }

  private def validatePaymentUpdate(id: PaymentId, data: PaymentEdit)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    find(id) match {
      case None => Left(serviceFailure("payment.update.notfound"))
      case Some(payment) => {
        Right(serviceSuccess("payment.update.success"))
      }
    }
  }
}
