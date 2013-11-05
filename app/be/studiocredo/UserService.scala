package be.studiocredo


import models.ids.UserId
import play.api.db.slick.Config.driver.simple._
import models.entities._
import scala.slick.session.Session
import models.schema.tables._
import com.google.inject.Inject


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

  def find(id: UserId)(implicit s: Session): Option[User] = {
    UsersQ.filter(_.id === id).firstOption
  }

  def findByUserName(name: String)(implicit s: Session): Option[User] = {
    UsersQ.filter(_.username.toLowerCase === name.toLowerCase).firstOption
  }

  def findByEmail(email: String)(implicit s: Session): List[RichUser] = {
    val query = for {
      u <- UsersQ
      d <- UserDetails if u.id === d.id && d.email.toLowerCase === email.toLowerCase
    } yield (u, d)
    query.list.map((ud) => RichUser(ud._1, ud._2))
  }


  def insert(user: UserEdit, details: UserDetailEdit)(implicit s: Session): UserId = {
    s.withTransaction {
      val userId = Users.autoInc.insert(user.copy(username = user.username.toLowerCase))
      UserDetails.insert(UserDetail(userId, details.email, details.address, details.phone))
      userId
    }
  }

  def update(id: UserId, user: UserEdit)(implicit s: Session) = {
    UsersQ.filter(_.id === id).update(User(id, user.name, user.username.toLowerCase, user.password))
  }
}
