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
import be.studiocredo.util.ServiceReturnValues._
import models.admin.RichUser
import models.entities.UserDetailEdit
import models.entities.UserDetail
import models.Page
import scala.Some
import models.entities.User
import models.entities.UserEdit
import be.studiocredo.auth.Password
import models.entities.UserRole
import models.admin.UserFormData
import views.helper.UserActiveOption

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

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, nameFilter: Option[String] = None, activeFilter: UserActiveOption.Option = UserActiveOption.default)(implicit s: Session): Page[RichUser] = {
    import models.queries._

    val offset = pageSize * page
    val baseQuery = UDQ.sortBy(_._1.id)
    val activeFilterQuery = activeFilter match {
          case UserActiveOption.Active => baseQuery.where(_._1.active === true)
          case UserActiveOption.Inactive => baseQuery.where(_._1.active === false)
          case UserActiveOption.Both => baseQuery
          case _ => baseQuery
    }
    val query = nameFilter.foldLeft(activeFilterQuery){
      (query, filter) => query.filter(q => iLike(q._1.name, s"%${filter}%")) // should replace with lucene
    }
    val total = query.length.run
    val values = paginate(query, page, pageSize).run map richUser
    Page(values, page, pageSize, offset, total)
  }

  def listInactive(implicit s: Session): List[RichUser] = {
    UDQ.filter(q => q._1.active === false).list.map(richUser)
  }

  def find(id: UserId)(implicit s: Session): Option[RichUser] = {
    UDQ.filter(q => q._1.id === id).firstOption.map(richUser)
  }

  def findUsers(ids: List[UserId])(implicit s: Session): List[User] = {
    UsersQ.filter(q => q.id inSet ids).list
  }

  def findByUserName(name: String)(implicit s: Session): Option[RichUser] = {
    UDQ.filter(q => q._1.username.toLowerCase === name.toLowerCase).firstOption.map(richUser)
  }

  def findActiveByUserName(name: String)(implicit s: Session): Option[RichUser] = {
    UDQ.filter(q => q._1.username.toLowerCase === name.toLowerCase && q._1.active).firstOption.map(richUser)
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

  def changePasswordAndActivate(email: String, username: String, password: Password)(implicit s: Session): Boolean = {
    s.withTransaction {
      activate(username)
      changePassword(email, username, password)
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
    find(id) map {um => UserFormData(um.name, um.username, um.email, um.address, um.phone, um.active)}
  }

  def insert(data: UserFormData)(implicit s: Session): UserId = {
    s.withTransaction {
      insert(UserEdit(data.name, data.username, Passwords.random(), data.active), UserDetailEdit(data.email, data.address, data.phone))
    }
  }

  def activate(username: String)(implicit s: Session) = {
    UsersQ.where(_.username === username).map(_.active).update(true)
  }

  //restrictions:
  //when there is no group yet => just add
  //when a group already exists => first remove group
  //avoid multilevel hierarchies
  //no circular references
  //all members of group should have same parent/master
  def createLoginGroup(ids: List[UserId])(implicit s: Session) = {
    ids.headOption match {
      case Some(loginGroup) => s.withTransaction {
        ids foreach (id => addToLoginGroup(id, loginGroup))
      }
      case None => ()
    }
  }

  def addToLoginGroup(userId: UserId, loginGroupId: UserId)(implicit s: Session) = {
    UsersQ.filter(_.id === userId).map(_.loginGroupId).update(Some(loginGroupId))
  }

  //when no group yet => ok
  //when group and not master => ok
  //when group and master =>
  //  if not last member => remove and elect other member as parent
  //  if last member => remove
  def removeLoginGroup(userId: UserId)(implicit s: Session) = {
    UsersQ.filter(_.id === userId).map(_.loginGroupId).update(None)
  }

  def findOtherUsers(user: User)(implicit s: Session): List[User] = {
    user.loginGroupId match {
      case Some(loginGroupId) => UsersQ.filter(q => q.loginGroupId === loginGroupId).where(_.id =!= user.id).list
      case None => List()
    }
  }

  def clearLoginGroups()(implicit s: Session) = {
    UsersQ.map(_.loginGroupId).update(None)
  }

  def update(id: UserId, data: UserFormData)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    validateUserUpdate(id, data.username).fold(
      failure => Left(failure),
      success => {
        s.withTransaction {
          val userUpdate = for {
            u <- UsersQ.filter(_.id === id)
          } yield u.name ~ u.username ~ u.active

          userUpdate.update((data.name, data.username.toLowerCase, data.active))
          val detailUpdate = for {
            u <- UsersDetailsQ.filter(_.id === id)
          } yield u.email ~ u.address ~ u.phone

          detailUpdate.update((data.email, data.address, data.phone))
        }
        Right(serviceSuccess("user.update.success"))
      })
  }

  private def validateUserUpdate(id: UserId, otherName: String)(implicit s: Session): Either[ServiceFailure, ServiceSuccess] = {
    find(id) match {
      case None => Left(serviceFailure("user.update.notfound"))
      case Some(user) => {
        if (user.username != otherName) {
          findByUserName(otherName) match {
            case None => Right(serviceSuccess("user.update.name.accepted"))
            case Some(otherUser) => Left(serviceFailure("user.update.name.used", List(otherName)))
          }
        } else {
          Right(serviceSuccess("user.update.name.unchanged"))
        }
      }
    }
  }
}
