package be.studiocredo.auth

import models.entities.Identity

trait Authorization {
  def isAuthorized(user: Identity): Boolean
}

object Authorization {

  class RoleAuthorization(role: Roles.Role) extends Authorization {
    def isAuthorized(user: Identity): Boolean = user.roles.contains(role)
  }

  val ADMIN = new RoleAuthorization(Roles.Admin)
  val MEMBER = new RoleAuthorization(Roles.Member)
}
