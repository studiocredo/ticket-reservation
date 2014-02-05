package controllers

import com.google.inject.Inject
import be.studiocredo._
import be.studiocredo.auth.{Authorization, Secure, AuthenticatorService}
import play.api.mvc.{Result, Controller}
import models.ids.{ShowId, EventId}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import be.studiocredo.auth.SecuredDBRequest
import models.entities.ShowPrereservation
import controllers.admin.routes
import models.admin.VenueShow

case class ShowPrereservationForm(showId: ShowId, quantity: Int)
case class PrereservationForm(showPrereservations: List[ShowPrereservationForm])

class Prereservations @Inject()(eventService: EventService, showService: ShowService, prereservationService: PreReservationService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {
  val defaultAuthorization = Some(Authorization.ANY)

  val prereservationForm: Form[PrereservationForm] = Form(
    mapping(
      "preres" -> list[ShowPrereservationForm] (
        mapping(
          "show" -> of[ShowId],
          "quantity" -> number
        )(ShowPrereservationForm.apply)(ShowPrereservationForm.unapply)
      )
    )(PrereservationForm.apply)(PrereservationForm.unapply)
  )

  //TODO: add ad-hoc contraints on maximum value (= totalquota) and sum of all values
  //http://www.playframework.com/documentation/2.2.x/ScalaForms

  def start(eventId: Option[EventId]) = AuthDBAction { implicit rs =>
    eventId match {
      case None => Redirect(routes.Application.index())
      case Some(id) => page(EventId(id))
    }
  }

  def save(id: EventId) = AuthDBAction { implicit rs =>
    prereservationForm.bindFromRequest.fold(
      formWithErrors => page(id, formWithErrors, BadRequest),
      success => {
        val userIds = rs.currentUser.get.id :: userContext.get.otherUsers.map{_.id}
        prereservationService.updateOrInsert(success.showPrereservations.map{ spr => ShowPrereservation(spr.showId, rs.currentUser.get.id, spr.quantity) }, userIds )
        Redirect(routes.Application.index).flashing("success" -> "Pre-reservaties bewaard")
      }
    )
  }

  private def page(eventId: EventId, form: Form[PrereservationForm] = prereservationForm, status: Status = Ok)(implicit rs: SecuredDBRequest[_]) = {
    val users = rs.currentUser match {
      case None => List()
      case Some(identity) => identity.id :: identity.otherUsers.map { _.id }
    }
    eventService.eventPrereservationDetails(eventId, users) match {
      case None => BadRequest(s"Evenement $eventId niet gevonden")
      case Some(event) => {
        val showPreresevations = event.shows.map{_.shows.map{_.id}}.flatten.map{id => ShowPrereservationForm(id, event.prereservationsByShow(id))}
        status(views.html.preorder(event, form.fill(PrereservationForm(showPreresevations)), userContext))
      }
    }
  }
}
