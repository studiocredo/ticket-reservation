package be.studiocredo.auth

object Roles {

  sealed trait Role {
    val id: String
  }

  case object Member extends Role {
    val id = "MEMBER"
  }

  case object Admin extends Role {
    val id = "ADMIN"
  }


  def toRole(id: String): Role = {
    id match {
      case Member.id => Member
      case Admin.id => Admin
      case _ => throw new IllegalArgumentException(s"$id not a role")
    }
  }

  def toString(role: Role): String = {
    role.id
  }
}

