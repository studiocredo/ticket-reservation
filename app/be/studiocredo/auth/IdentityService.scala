package be.studiocredo.auth

import models.ids.UserId
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import models.entities._
import play.api.Play.current
import models.schema.tables._
import com.google.inject.Inject
import be.studiocredo.UserService
import scala.slick.session.Session
import models.admin.RichUser
import models.schema.tables.Users

class IdentityService @Inject()(userService: UserService) {
  val UsersQ = Query(Users)


  def find(id: UserId): Option[Identity] = {
    DB.withSession { implicit session =>
      userService.find(id) map toIdentity
    }
  }

  def findByUserName(user: String): Option[Identity] = {
    DB.withSession { implicit session =>
      userService.findActiveByUserName(user) map toIdentity
    }
  }

  def toIdentity(user: RichUser)(implicit s: Session): Identity = {
    Identity(user, userService.findRoles(user.id), userService.findOtherUsers(user.user))
  }
}

