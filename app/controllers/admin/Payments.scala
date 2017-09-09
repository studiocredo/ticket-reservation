package controllers.admin

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.AuthenticatorService
import play.api.data.Form
import play.api.data.Forms._
import models.ids._
import models.entities._
import models.entities.PaymentType.PaymentType
import be.studiocredo.util.Money
import be.studiocredo.util.ServiceReturnValues._
import models.entities.PaymentEdit
import scala.Some
import views.helper.{PaymentRegisteredOption, Options}


case class PaymentSearchForm(search: Option[String], registered: PaymentRegisteredOption.Option)

class Payments @Inject()(paymentService: PaymentService, orderService: OrderService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  val ListPage = Redirect(routes.Payments.list())

  import Options._
  val paymentTypeOptions = Options.apply(PaymentType.values.toSeq, PaymentTypeRenderer)

  val paymentSearchForm = Form(
    mapping(
      "search" -> optional(nonEmptyText(3)),
      "registered" -> of[PaymentRegisteredOption.Option]
    )(PaymentSearchForm.apply)(PaymentSearchForm.unapply)
  )

  val paymentForm = Form(
    mapping(
      "paymentType" -> of[PaymentType],
      "importId" -> optional(text),
      "orderId" -> optional(of[OrderId]),
      "debtor" -> nonEmptyText,
      "amount" -> of[Money],
      "message" -> optional(text),
      "details" -> optional(text),
      "date" -> jodaDate("yyyy-MM-dd HH:mm"),
      "archived" -> boolean
    )(PaymentEdit.apply)(PaymentEdit.unapply)
  )
  
  def list(search: Option[String], registered: String, showAll: Boolean, page: Int) = AuthDBAction { implicit rs =>
    val bindedForm = paymentSearchForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => {
        val list = paymentService.page(page, showAll)
        Ok(views.html.admin.payments(list, formWithErrors, showAll, userContext))
      },
      paymentFormData => {
        val list = paymentService.page(page, showAll, 10, 1, paymentFormData.search, paymentFormData.registered)
        Ok(views.html.admin.payments(list, bindedForm, showAll, userContext))
      }
    )
  }

  def delete(id: PaymentId) = AuthDBAction { implicit request =>
    paymentService.find(id) match {
      case None => ListPage.flashing("error" -> s"Betaling '$id' niet gevonden")
      case Some(payment) => {
        paymentService.delete(id)
        ListPage.flashing("success" -> s"Betaling '$id' gearchiveerd")
      }
    }
  }

  def create() = AuthDBAction { implicit request =>
    Ok(views.html.admin.paymentsCreateForm(paymentForm, getOrderOptions(orderService.all), paymentTypeOptions, userContext))
  }

  private def getOrderOptions(orders: Seq[Order]): Options[Option[Order]] = {
    import Options._
    Options.apply(None +: orders.map(Some(_)), OrderRenderer)
  }

  def save() = AuthDBAction { implicit rs =>
    val bindedForm = paymentForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => {
        BadRequest(views.html.admin.paymentsCreateForm(formWithErrors, getOrderOptions(orderService.all), paymentTypeOptions, userContext))
      },
      payment => {
        paymentService.insert(payment).fold(
          error => BadRequest(views.html.admin.paymentsCreateForm(bindedForm.withGlobalError(serviceMessage(error)), getOrderOptions(orderService.all), paymentTypeOptions, userContext)),
          success => ListPage.flashing("success" -> "Betaling aangemaakt")
        )
      }
    )
  }

  def edit(id: PaymentId) = AuthDBAction { implicit rs =>
    paymentService.getEdit(id) match {
      case None => ListPage
      case Some(payment) => Ok(views.html.admin.paymentsEditForm(id, paymentForm.fillAndValidate(payment), getOrderOptions(orderService.all), paymentTypeOptions, userContext))
    }
  }

  def copy(id: PaymentId) = AuthDBAction { implicit rs =>
    paymentService.getEdit(id) match {
      case None => ListPage
      case Some(payment) => {
        val newDetails = Some(Seq(Some(s"Kopie van betaling ${id}"), payment.details).flatten.mkString("\n"))
        Ok(views.html.admin.paymentsCreateForm(paymentForm.fillAndValidate(payment.copy(importId = None, details = newDetails)), getOrderOptions(orderService.all), paymentTypeOptions, userContext))
      }
    }
  }

  def update(id: PaymentId) = AuthDBAction { implicit rs =>
    val bindedForm = paymentForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => BadRequest(views.html.admin.paymentsEditForm(id, formWithErrors, getOrderOptions(orderService.all), paymentTypeOptions, userContext)),
      payment => {
        paymentService.update(id, payment).fold(
          error => BadRequest(views.html.admin.paymentsEditForm(id, bindedForm.withGlobalError(serviceMessage(error)), getOrderOptions(orderService.all), paymentTypeOptions, userContext)),
          success => ListPage.flashing("success" -> "Betaling aangepast")
        )
      }
    )
  }

  def upload() = AuthDBAction(parse.multipartFormData) { implicit request =>
    request.body.file("transactions").map { transcations =>
      val filename = transcations.filename
      val contentType = transcations.contentType
      val payments = paymentService.insertTransactions(transcations.ref.file)
      transcations.ref.clean()
      //transcations.ref.moveTo(new File(s"/tmp/transactions/$filename"))
      ListPage.flashing(
        "success" -> s"${payments.length} nieuwe betalingen geïmporteerd"
      )
    }.getOrElse {
      ListPage.flashing(
        "error" -> "Bestand niet gevonden")
    }
  }
}
