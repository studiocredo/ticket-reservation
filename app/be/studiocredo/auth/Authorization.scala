package be.studiocredo.auth

trait Authorization {
  def isAuthorized(user: Identity): Boolean
}


object Authorization {

  class RoleAuthorization(role: Roles.Role) extends Authorization {
    def isAuthorized(user: Identity): Boolean = user.role == role
  }

  val ADMIN = new RoleAuthorization(Roles.Admin)
  val MEMBER = new RoleAuthorization(Roles.Member)
  val GUEST = new RoleAuthorization(Roles.Guest)
}
