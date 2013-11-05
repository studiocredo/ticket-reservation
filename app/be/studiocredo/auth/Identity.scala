package be.studiocredo.auth


import Roles._
import org.joda.time.DateTime
import models.entities.User


case class Identity(user: User, role: Role) {
  def id = user.id
  def name = user.name
  def username = user.username
  def password = user.password
}

case class Password(hashed: String, salt: String)

object Roles {
  sealed trait Role
  case object Guest extends Role
  case object Member extends Role
  case object Admin extends Role
}

