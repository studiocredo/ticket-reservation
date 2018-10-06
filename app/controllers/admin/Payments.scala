package controllers.admin

import be.studiocredo._
import be.studiocredo.account.AccountStatementImportService
import be.studiocredo.account.AccountStatementImportService.CodaboxSyncStatus
import be.studiocredo.auth.AuthenticatorService
import be.studiocredo.util.Money
import be.studiocredo.util.ServiceReturnValues._
import com.google.inject.Inject
import models.entities.PaymentType.PaymentType
import models.entities.{PaymentEdit, _}
import models.ids._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files
import play.api.mvc.{Action, AnyContent, MultipartFormData, SimpleResult}
import views.helper.{Options, PaymentRegisteredOption}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class PaymentSearchForm(search: Option[String], registered: PaymentRegisteredOption.Option)

class Payments @Inject()(paymentService: PaymentService, orderService: OrderService, accountStatementImportService: AccountStatementImportService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  val ListPage: SimpleResult = Redirect(routes.Payments.list())

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

  def list(search: Option[String], registered: String, showAll: Boolean, page: Int): Action[AnyContent] = AuthDBAction.async { implicit rs =>
    accountStatementImportService.info().map { codaboxInfo =>
      val bindedForm = paymentSearchForm.bindFromRequest
      bindedForm.fold(
        formWithErrors => {
          val list = paymentService.page(page, showAll)
          Ok(views.html.admin.payments(list, formWithErrors, showAll, accountStatementImportService.upload, codaboxInfo, userContext))
        },
        paymentFormData => {
          val list = paymentService.page(page, showAll, 10, 1, paymentFormData.search, paymentFormData.registered)
          Ok(views.html.admin.payments(list, bindedForm, showAll, accountStatementImportService.upload, codaboxInfo, userContext))
        }
      )
    }
  }

  def delete(id: PaymentId): Action[AnyContent] = AuthDBAction.async { implicit request =>
    paymentService.find(id).map { payment =>
      paymentService.delete(id)
      accountStatementImportService.update(Seq(payment), CodaboxSyncStatus.Skipped)
        .map {
          case Some(response) if response.failed == 0 => ListPage.flashing("success" -> s"Betaling '$id' gearchiveerd")
          case _ => ListPage.flashing("warning" -> s"Betaling '$id' gearchiveerd, fout tijdens synchroniseren")
        }
    }.getOrElse(Future.successful(ListPage.flashing("error" -> s"Betaling '$id' niet gevonden")))
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
      case Some(payment) =>
        val newDetails = Some(Seq(Some(s"Kopie van betaling $id"), payment.details).flatten.mkString("\n"))
        Ok(views.html.admin.paymentsCreateForm(paymentForm.fillAndValidate(payment.copy(importId = None, details = newDetails)), getOrderOptions(orderService.all), paymentTypeOptions, userContext))
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

  def upload(): Action[MultipartFormData[Files.TemporaryFile]] = AuthDBAction.async(parse.multipartFormData) { implicit request =>
    request.body.file("transactions").fold {
      Future.apply(ListPage.flashing("error" -> "Bestand niet gevonden"))
    } { data =>
      accountStatementImportService.extract(Some(data.ref.file)).map(paymentService.upload).map { p =>
        data.ref.clean()
        p
      }.map(payments => ListPage.flashing("success" -> s"${payments.length} nieuwe betalingen geïmporteerd"))
    }
  }

  def importCodabox(): Action[AnyContent] = AuthDBAction.async { implicit request =>
    accountStatementImportService.extract(None).map(paymentService.upload)
      .flatMap(payments => accountStatementImportService.update(payments, CodaboxSyncStatus.Processed))
      .map {
        case Some(response) if response.failed > 0 => ListPage.flashing("warning" -> s"${response.updated} nieuwe betalingen geïmporteerd, ${response.failed} fouten")
        case Some(response) => ListPage.flashing("success" -> s"${response.updated} nieuwe betalingen geïmporteerd")
        case _ => ListPage.flashing("error" -> s"Fout tijdens importeren van betalingen")
      }
  }
}
