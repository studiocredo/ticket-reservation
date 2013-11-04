package controllers

import play.api._
import play.api.mvc._
import play.api.data._


object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }


  def javascriptRoutes = Action { implicit request =>
    import admin.routes.javascript._
     Ok(
      Routes.javascriptRouter("jsRoutes")(
        GroupDetails.ajaxMembers, MemberDetails.ajaxGroups
      )).as("text/javascript")
  }

}
