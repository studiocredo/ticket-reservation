package be.studiocredo.auth

object Roles {

  sealed trait Role {
    val id: String
  }

  case object Admin extends Role {
    val id = "ADMIN"
  }

  case class UnknownRole(id: String) extends Role


  def toRole(id: String): Role = {
    id match {
      case Admin.id => Admin
      case _ => UnknownRole(id)
    }
  }

  def toString(role: Role): String = {
    role.id
  }
}

