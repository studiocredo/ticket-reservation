package models.database

import play.api.db.slick.Config.driver.simple._

class Users extends Table[(Long, String, String, String)]("users") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def email = column[String]("email")
  def password = column[String]("password")

  def * = id ~ name ~ email ~ password

  def uniqueEmail = index("idx_email", email, unique = true)
}


class Member extends Table[(Long, Long)]("members") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def userId = column[Long]("user_id")

  def * = id ~ userId

  def user = foreignKey("user_fk", userId, new Users)(_.id)
}
