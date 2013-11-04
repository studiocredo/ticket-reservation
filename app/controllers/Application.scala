package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import be.studiocredo.ShowService
import play.api.db.slick._
import play.api.Play.current


object Application extends Controller {

  val showService = new ShowService

  def index = DBAction { implicit request =>
    Ok(views.html.index(showService.nextShows(3)))
  }


  def javascriptRoutes = Action { implicit request =>
    import admin.routes.javascript._
     Ok(
      Routes.javascriptRouter("jsRoutes")(
        GroupDetails.ajaxMembers, MemberDetails.ajaxGroups
      )).as("text/javascript")
  }

}
