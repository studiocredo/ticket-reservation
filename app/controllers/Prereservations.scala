package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.{Authorization, Secure, AuthenticatorService}
import play.api.mvc.Controller
import models.ids.{ShowId, EventId}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import be.studiocredo.auth.SecuredDBRequest
import models.entities.{ShowPrereservationUpdate, ShowPrereservation}
import be.studiocredo.util.ServiceReturnValues._
import controllers.auth.Mailer


case class ShowPrereservationForm(showId: ShowId, quantity: Int)
case class PrereservationForm(showPrereservations: List[ShowPrereservationForm])

class Prereservations @Inject()(eventService: EventService, showService: ShowService, prereservationService: PreReservationService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {
  val defaultAuthorization = Some(Authorization.ANY)

  val prereservationForm: Form[PrereservationForm] = Form(
    mapping(
      "preres" -> list[ShowPrereservationForm] (
        mapping(
          "show" -> of[ShowId],
          "quantity" -> number(min = 0)
        )(ShowPrereservationForm.apply)(ShowPrereservationForm.unapply)
      )
    )(PrereservationForm.apply)(PrereservationForm.unapply)
  )

  def start(id: EventId) = AuthDBAction { implicit rs =>
    page(id)
  }

  def save(id: EventId) = AuthDBAction { implicit rs =>
    val bindedForm = prereservationForm.bindFromRequest
    bindedForm.fold(
      formWithErrors => page(id, Some(formWithErrors), BadRequest),
      preres => {
        val currentUser = rs.currentUser.get
        val userIds = currentUser.allUsers
        validatePrereservations(bindedForm, preres, prereservationService.totalQuotaByUsersAndEvent(userIds, id)).fold(
          formWithErrors => page(id, Some(formWithErrors), BadRequest),
          success => {
            prereservationService.updateOrInsert(id, preres.showPrereservations.map{ spr => ShowPrereservationUpdate(spr.showId, spr.quantity) }, userIds ).fold(
              failure => page(id, Some(bindedForm.withGlobalError(serviceMessage(failure))), BadRequest),
              success => {
                Mailer.sendPrereservationSavedEmail(currentUser.user, eventService.eventPrereservationDetails(id, userIds))
                Redirect(routes.Application.index).flashing("success" -> serviceMessage(success))
              }
            )
          }
        )
      }
    )
  }

  private def page(eventId: EventId, form: Option[Form[PrereservationForm]] = None, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.allUsers
    }
    eventService.eventPrereservationDetails(eventId, users) match {
      case None => BadRequest(s"Evenement $eventId niet gevonden")
      case Some(event) => {
        form match {
          case Some(form) => status(views.html.preorder(event, form , userContext))
          case _ => {
            val showPrereservations = event.orderedShows.map{case (v,s) => s.id}.map{id => ShowPrereservationForm(id, event.prereservationsByShow(id))}
            status(views.html.preorder(event, prereservationForm.fill(PrereservationForm(showPrereservations)), userContext))
          }
        }
      }
    }
  }

  //two step validation: http://workwithplay.com/blog/2013/07/10/advanced-forms-techniques/
  private def validatePrereservations(form: Form[PrereservationForm], preres: PrereservationForm, totalQuotaOption: Option[Int]): Either[Form[PrereservationForm], PrereservationForm] = {
    val totalQuota = totalQuotaOption.getOrElse(0)
    preres.showPrereservations.map{_.quantity}.sum match {
      case sum if sum <= totalQuota => Right(preres)
      case _ => {
        val message = totalQuota match {
          case 0 => s"Je hebt geen recht op pre-reservaties"
          case 1 => s"Je hebt recht op $totalQuota pre-reservatie"
          case _ => s"Je hebt recht op maximum $totalQuota pre-reservaties"
        }
        Left(form.withGlobalError(message))
      }
    }
  }
}
