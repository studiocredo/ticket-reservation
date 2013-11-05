package be.studiocredo.auth

import models.ids.UserId
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import models.entities._
import play.api.Play.current
import models.schema.tables._
import com.google.inject.Inject

class IdentityService @Inject()() {
  val UsersQ = Query(Users)

  def userToIdentity(user: User): Identity = Identity(user, Roles.Member) // todo role

  def find(id: UserId): Option[Identity] = {
    DB.withSession { implicit session =>
      UsersQ.filter(_.id === id).firstOption map userToIdentity
    }
  }

  def findByUserName(user: String): Option[Identity] = {
    DB.withSession { implicit session =>
      UsersQ.filter(_.username.toLowerCase === user.toLowerCase).firstOption map userToIdentity
    }
  }

}

