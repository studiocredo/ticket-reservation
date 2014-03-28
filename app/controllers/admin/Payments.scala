package controllers.admin

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.AuthenticatorService
import play.api.data.Form
import play.api.data.Forms._
import scala.Some

case class PaymentSearchForm(search: String)

class Payments @Inject()(paymentService: PaymentService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {

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

  def create() = AuthDBAction { implicit request =>
    ???
  }
}
