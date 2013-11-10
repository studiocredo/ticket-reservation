package be.studiocredo


import models.ids.UserId
import play.api.db.slick.Config.driver.simple._
import models.entities._
import scala.slick.session.Session
import models.schema.tables._
import com.google.inject.Inject
import be.studiocredo.auth.Password
import be.studiocredo.auth.Roles


case class RichUser(user: User, detail: UserDetail) {
  def id = user.id
  def name = user.name
  def username = user.username
  def password = user.password
  def email = detail.email
  def address = detail.address
  def phone = detail.phone
}

class UserService @Inject()() {
  val UsersQ = Query(Users)
  val UserRolesQ = Query(UserRoles)

  val UDQ = for {
    u <- UsersQ
    d <- UserDetails if u.id === d.id
  } yield (u, d)

  def richUser: ((User, UserDetail)) => RichUser = {
    (ud) => RichUser(ud._1, ud._2)
  }

  def find(id: UserId)(implicit s: Session): Option[RichUser] = {
    UDQ.filter(q => q._1.id === id).firstOption.map(richUser)
  }

  def findByUserName(name: String)(implicit s: Session): Option[RichUser] = {
    UDQ.filter(q => q._1.username.toLowerCase === name.toLowerCase).firstOption.map(richUser)
  }

  def findByEmail(email: String)(implicit s: Session): List[RichUser] = {
    UDQ.filter(q => q._2.email.toLowerCase === email.toLowerCase).list.map(richUser)
  }

  def find(email: String, username: String)(implicit s: Session): Option[RichUser] = {
    UDQ.filter(q => q._1.username.toLowerCase === username.toLowerCase && q._2.email.toLowerCase === email.toLowerCase).firstOption.map(richUser)
  }

  def insert(user: UserEdit, details: UserDetailEdit)(implicit s: Session): UserId = {
    s.withTransaction {
      val userId = Users.autoInc.insert(user.copy(username = user.username.toLowerCase))
      UserDetails.insert(UserDetail(userId, details.email, details.address, details.phone))
      userId
    }
  }

  def update(id: UserId, user: UserEdit)(implicit s: Session) = {
    val uq = for {
      u <- UsersQ.filter(_.id === id)
    } yield u.name ~ u.username ~ u.salt ~ u.password
    uq.update((user.name, user.username.toLowerCase, user.password.salt, user.password.hashed))
  }

  def changePassword(email: String, username: String, password: Password)(implicit s: Session): Boolean = {
    s.withTransaction {
      find(email, username) match {
        case Some(user) => changePassword(user.id, password)
        case None => false
      }
    }
  }

  def changePassword(id: UserId, password: Password)(implicit s: Session): Boolean = {
    s.withTransaction {
      (for {
        u <- UsersQ if u.id === id
      } yield u.salt ~ u.password).update((password.salt, password.hashed)) == 1
    }
  }

  def addRole(id: UserId, role: Roles.Role)(implicit s: Session) {
    UserRoles.insert(UserRole(id, role))
  }


  def findRoles(id: UserId)(implicit s: Session): List[Roles.Role] = {
    UserRolesQ.filter(_.userId === id).list map (_.role)
  }
}
