package controllers

import play.api.mvc.{Action, Controller}
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import views.html
import models.entities.User

import utils.DBAccess

/*
object SignUp extends Controller with DBAccess {

  val signupForm: Form[User] = Form(
    mapping(
      "name" -> text(minLength = 2),
      "email" -> email,

      "password" -> tuple(
        "main" -> text(minLength = 6),
        "confirm" -> text
      ).verifying(
        "Passwords don't match", passwords => passwords._1 == passwords._2
      )
    ) {
      (name, email, passwords) => User(None, name, email, passwords._1)
    } {
      user => Some(user.name, user.email, (user.password, ""))
    }.verifying(
      "Using with this email address already exists",
      user => {
        withSession {
          implicit session =>
            Users.emailExists(user.email)
        }
      }
    )
  )

  def form = Action {
    Ok(html.signup.form(signupForm))
  }

  def submit = Action {
    implicit request =>
      signupForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => BadRequest(html.signup.form(errors)),
        user => {
          withSession {
            implicit session =>
              Users.insert(user)
          }
          Ok(html.index())
        }
      )
  }

}
*/
