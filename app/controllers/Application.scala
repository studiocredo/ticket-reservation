package controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.main("Index", None)(views.html.index()))
  }

}
