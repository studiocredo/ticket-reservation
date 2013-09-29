package controllers

import play.api._
import play.api.mvc._
import models.database.Users
import scala.slick.lifted.Query
import play.api.db.slick.Config.driver.simple._

object Application extends Controller {

  def index = Action {
    Logger.info((new Users).ddl.createStatements.mkString)
    Logger.info((Query(new Users).selectStatement))
    Ok(views.html.main("Index")(views.html.index()))
  }

}
