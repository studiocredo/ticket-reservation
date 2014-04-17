package controllers.admin

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.AuthenticatorService
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import models.ids.PaymentId

case class PaymentSearchForm(search: String)

class Payments @Inject()(paymentService: PaymentService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

  val ListPage = Redirect(routes.Payments.list())

  val paymentSearchForm = Form(
    mapping(
      "search" -> nonEmptyText(3)
    )(PaymentSearchForm.apply)(PaymentSearchForm.unapply)
  )
  
  def list(page: Int) = AuthDBAction { implicit rs =>
    val bindedForm = paymentSearchForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => {
        val list = paymentService.page(page)
        Ok(views.html.admin.payments(list, formWithErrors, userContext))
      },
      paymentFormData => {
        val list = paymentService.page(page, 10, 1, Some(paymentFormData.search))
        Ok(views.html.admin.payments(list, bindedForm, userContext))
      }
    )
  }

  def archive(id: PaymentId) = AuthDBAction { implicit request =>
    paymentService.find(id) match {
      case None => ListPage.flashing("error" -> s"Betaling '$id' niet gevonden")
      case Some(payment) => {
        paymentService.archive(id)
        ListPage.flashing("success" -> s"Betaling '$id' gearchiveerd")
      }
    }
  }

  def create() = AuthDBAction { implicit request =>
    ???
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
