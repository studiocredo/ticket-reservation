package controllers.admin

import be.studiocredo.auth.{Authorization, Secure}
import play.api.mvc.Controller

trait AdminController extends Controller with Secure {

  val defaultAuthorization = Some(Authorization.ADMIN)
}
