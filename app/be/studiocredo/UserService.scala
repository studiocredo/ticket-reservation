package be.studiocredo


import models.ids.UserId
import play.api.db.slick.Config.driver.simple._
import models.entities._
import scala.slick.session.Session
import models.schema.tables._
import com.google.inject.Inject
import be.studiocredo.auth.{Passwords, Password, Roles}
import models.admin._
import models.Page

class UserService @Inject()() {
  val UsersQ = Query(Users)
  val UsersDetailsQ = Query(UserDetails)
  val UserRolesQ = Query(UserRoles)

  val UDQ = for {
    u <- UsersQ
    d <- UserDetails if u.id === d.id
  } yield (u, d)

  def richUser: ((User, UserDetail)) => RichUser = {
    (ud) => RichUser(ud._1, ud._2)
  }


  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[RichUser] = {
    import models.queries._

    val offset = pageSize * page
    val total = UDQ.length.run
    val values = filter.foldLeft {
      paginate(UDQ, page, pageSize)
    } {
      (query, filter) => query.filter(q => iLike(q._1.name, filter)) // should replace with lucene
    }.run map richUser
    Page(values, page, pageSize, offset, total)
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


  def getEdit(id: UserId)(implicit s: Session): Option[UserFormData] = {
    find(id) map {um => UserFormData(um.name, um.username, um.email, um.address, um.phone)}
  }

  def insert(data: UserFormData)(implicit s: Session): UserId = {
    s.withTransaction {
      insert(UserEdit(data.name, data.username, Passwords.random()), UserDetailEdit(data.email, data.address, data.phone))
    }
  }

  def update(id: UserId, data: UserFormData)(implicit s: Session) = {
    s.withTransaction {
      val userUpdate = for {
        u <- UsersQ.filter(_.id === id)
      } yield u.name ~ u.username

      userUpdate.update((data.name, data.username.toLowerCase))
      val detailUpdate = for {
        u <- UsersDetailsQ.filter(_.id === id)
      } yield u.email ~ u.address ~ u.phone

      detailUpdate.update((data.email, data.address, data.phone))
    }
  }
}
