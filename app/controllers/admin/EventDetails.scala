package controllers.admin

import be.studiocredo._
import be.studiocredo.auth.{AuthenticatorService, SecuredDBRequest}
import be.studiocredo.util.Money
import com.google.inject.Inject
import models.admin._
import models.ids._
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Invalid
import play.api.mvc.SimpleResult
import views.helper.Options

class EventDetails @Inject()(eventService: EventService, showService: ShowService, venueService: VenueService, assetService: AssetService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends AdminController with UserContextSupport {
  val logger = Logger("group-details")

  implicit val dateFormatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  val showForm = Form(
    mapping(
      "venue" -> of[VenueId],
      "date" -> jodaDate("yyyy-MM-dd HH:mm"),
      "reservationStart" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "reservationEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "archived" -> boolean
    )(ShowEdit.apply)(ShowEdit.unapply)
  )

  val assetForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "price" -> optional(of[Money]),
      "availableStart" -> jodaDate("yyyy-MM-dd HH:mm"),
      "availableEnd" -> optional(jodaDate("yyyy-MM-dd HH:mm")),
      "downloadable" -> boolean,
      "objectKey" -> optional(text),
      "archived" -> boolean
    )(AssetEdit.apply)(AssetEdit.unapply)
  )

  def view(id: EventId) = AuthDBAction { implicit rs =>
    page(id)
  }

  def addShow(id: EventId) = AuthDBAction { implicit rs =>
    val formWithBindings = showForm.bindFromRequest
    formWithBindings.fold(
      formWithErrors => page(id, formWithErrors, assetForm, BadRequest),
      newShow => {
        eventService.get(id).fold {
          page(id, formWithBindings.withGlobalError("error.event.not_found"), assetForm, BadRequest)
        } { event =>
          ShowConstraints.forEvent(event).map(_.apply(newShow)).collect { case i: Invalid => i } match {
            case Nil =>
              showService.insert(id, newShow)
              Redirect(routes.EventDetails.view(id)).flashing("success" -> "Voorstelling toegevoegd")
            case list =>
              val formWithErrors = list.flatMap(_.errors).foldLeft(formWithBindings){ case(form, error) => form.withGlobalError(error.message, error.args)}
              page(id, formWithErrors, assetForm, BadRequest)
          }
        }
      }
    )
  }

  def addAsset(id: EventId) = AuthDBAction { implicit rs =>
    assetForm.bindFromRequest.fold(
      formWithErrors => page(id, showForm, formWithErrors, BadRequest),
      newAsset => {
        assetService.insert(id, AssetEdit(newAsset.name, newAsset.price, newAsset.availableStart, newAsset.availableEnd, newAsset.downloadable, newAsset.objectKey, newAsset.archived))

        Redirect(routes.EventDetails.view(id)).flashing("success" -> "Item toegevoegd")
      }
    )
  }

  def page(id: EventId, showForm: Form[ShowEdit] = showForm, assetForm: Form[AssetEdit] = assetForm, status: Status = Ok)(implicit rs: SecuredDBRequest[_]): SimpleResult = {
    eventService.eventDetails(id) match {
      case None => BadRequest(s"Evenement $id niet gevonden")
      case Some(details) => status(views.html.admin.event(details, views.html.admin.eventAddShow(id, showForm, Options.apply(venueService.list(), Options.VenueRenderer)), views.html.admin.eventAddAsset(id, assetForm), userContext))
    }
  }

  def editShow(id: EventId, showId: ShowId) = AuthDBAction { implicit rs =>
    showPage(id, showId)
  }

  def showPage(id: EventId, showId: ShowId, showForm: Form[ShowEdit] = showForm, load: Boolean = true , status: Status = Ok)(implicit rs: SecuredDBRequest[_]): SimpleResult = {
    showService.get(showId) match {
      case None => BadRequest(s"Show $id niet gevonden")
      case Some(show) =>
        val filledForm = if (load) showForm.fill(ShowEdit(show.venueId, show.date, show.reservationStart, show.reservationEnd, show.archived)) else showForm
        status(views.html.admin.show(id, showId, filledForm, Options.apply(venueService.list(), Options.VenueRenderer),  userContext))
    }
  }

  def editAsset(id: EventId, assetId: AssetId) = AuthDBAction { implicit rs =>
    assetPage(id, assetId)
  }

  def assetPage(id: EventId, assetId: AssetId, assetForm: Form[AssetEdit] = assetForm, load: Boolean = true , status: Status = Ok)(implicit rs: SecuredDBRequest[_]): SimpleResult = {
    assetService.get(assetId) match {
      case None => BadRequest(s"Item $id niet gevonden")
      case Some(asset) =>
        val filledForm = if (load) assetForm.fill(AssetEdit(asset.name, asset.price, asset.availableStart, asset.availableEnd, asset.downloadable, asset.objectKey, asset.archived)) else assetForm
        status(views.html.admin.asset(id, assetId, filledForm, userContext))
    }
  }

  def updateShow(id: EventId, showId: ShowId) = AuthDBAction { implicit rs =>
    val formWithBindings = showForm.bindFromRequest
    formWithBindings.fold(
      formWithErrors => showPage(id, showId, formWithErrors, load = false, BadRequest),
      show => {
        eventService.get(id).fold {
          showPage(id, showId, formWithBindings.withGlobalError("error.event.not_found"), load = false, BadRequest)
        } { event =>
          ShowConstraints.forEvent(event).map(_.apply(show)).collect { case i: Invalid => i } match {
            case Nil =>
              showService.update(showId, show)
              Redirect(routes.EventDetails.view(id)).flashing("success" -> "Voorstelling aangepast")
            case list =>
              val formWithErrors = list.flatMap(_.errors).foldLeft(formWithBindings){ case(form, error) => form.withGlobalError(error.message, error.args)}
              showPage(id, showId, formWithErrors, load = false, BadRequest)
          }
        }
      }
    )
  }

  def updateAsset(id: EventId, assetId: AssetId) = AuthDBAction { implicit rs =>
    assetForm.bindFromRequest.fold(
      formWithErrors => assetPage(id, assetId, formWithErrors, load = false, BadRequest),
      asset => {
        assetService.update(assetId, asset)

        Redirect(routes.EventDetails.view(id)).flashing("success" -> "Item aangepast")
      }
    )
  }

  def deleteShow(id: EventId, showId: ShowId) = AuthDBAction { implicit rs =>
    showService.delete(showId)
    Redirect(routes.EventDetails.view(id)).flashing("success" -> "Voorstelling verwijderd")
  }

  def deleteAsset(id: EventId, assetId: AssetId) = AuthDBAction { implicit rs =>
    assetService.delete(assetId)
    Redirect(routes.EventDetails.view(id)).flashing("success" -> "Item verwijderd")
  }

}


