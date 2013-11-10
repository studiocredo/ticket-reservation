package controllers.auth

import play.api.mvc.{Controller, SimpleResult, Call}
import be.studiocredo.auth.{AuthenticatorService, SecureRequest, EmailToken}

trait AuthUtils extends Controller {
  def authService: AuthenticatorService

  protected def executeForToken(token: String, error: Call, f: EmailToken => SimpleResult)(implicit request: SecureRequest[_]): SimpleResult = {
    authService.checkEmailToken(token).fold(
      Redirect(error).flashing("error" -> "The link you followed no longer valid")
    )(
      f
    )
  }

}
