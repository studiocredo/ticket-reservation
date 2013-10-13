package models

import play.api.db.slick.Config.driver.simple._
import utils.DBAccess


class UserService extends DBAccess {

  def emailExists(email: String)(implicit session: Session): Boolean = {
    database withSession {
      Users.filter(u => u.email == email).length == 0
    }
  }

  def insert(user: User)(implicit session: Session): Long = {
    require(user.id == None)
    database withSession {
      Users.insert(user)
    }
  }

  def update(user: User)(implicit session: Session): Long = {
    require(user.id != None)
    database withSession {
      Users.filter(u => u.id == user.id).update(user)
    }
  }
}
