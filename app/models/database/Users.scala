package models.database

import play.api.db.slick.Config.driver.simple._
import models.User
import utils.DBAccess


object Users extends DBAccess {
  def emailExists(email: String)(implicit session: Session): Boolean = {
    database withSession {
      Users.filter(u => u.email == email).length == 0
    }
  }

  def insert(user: User)(implicit session: Session): Long = {
    require(user.id == None)
    database withSession {
      Users.forInsert returning Users.id insert user
    }
  }

  def update(user: User)(implicit session: Session): Long = {
    require(user.id != None)
    database withSession {
      Users.filter(u => u.id == user.id).update(user)
    }
  }

  object Users extends Table[User]("users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[String]("email")
    def password = column[String]("password")

    def * = id.? ~ name ~ email ~ password <>(User, User.unapply _)
    // WTF project without id so it can be auto incremented db side
    def forInsert = name ~ email ~ password <> ( { t => User(None, t._1, t._2, t._3) }, { (u: User) => Some((u.name, u.email, u.password))})

    def uniqueEmail = index("idx_email", email, unique = true)
  }


  class Member extends Table[(Long, Long)]("members") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")

    def * = id ~ userId

    def user = foreignKey("user_fk", userId, Users)(_.id)
  }
}
