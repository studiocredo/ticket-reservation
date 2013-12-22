package views.helper

import play.api.templates.Html
import be.studiocredo.auth.Roles.{Admin, Role}
import be.studiocredo.auth.{SecureRequest, Authorization, Roles}

object Auth {

  def restrictAdmin(content: Html)(implicit request: SecureRequest[_]): Html = restrict(Authorization.ADMIN)(content)

  def restrict(auth: Authorization)(content: Html)(implicit request: be.studiocredo.auth.SecureRequest[_]): Html = {
    if (request.currentUser.isDefined && auth.isAuthorized(request.currentUser.get))
      content
    else
      Html.empty
  }

}
