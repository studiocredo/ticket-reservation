package be.studiocredo

import play.api.db.slick.Config.driver.simple._
import models.entities._
import models.admin._
import models.Page
import scala.slick.session.Session
import models.ids._
import com.google.inject.Inject
import be.studiocredo.auth.{Roles, Passwords}
import models.schema.tables._

object MemberQueries {
  val MembersQ = Query(Members)
  val UsersQ = Query(Users)
  val UserRolesQ = Query(UserRoles)

  val members = for {
    m <- MembersQ
    u <- m.user
    d <- UserDetails if u.id === d.id
  } yield (m, u, d)

  val active = members.filter(_._1.archived === false)

  def toUserMember(data:(Member, User, UserDetail)) = UserMember(RichUser(data._2, data._3), data._1)
}

class MemberService @Inject()(userService: UserService) {

  import models.queries._
  import models.schema.tables._

  import MemberQueries._

  def page(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: Option[String] = None)(implicit s: Session): Page[UserMember] = {
    val offset = pageSize * page
    val total = active.length.run
    val values = filter.foldLeft {
      paginate(active, page, pageSize)
    } {
      (query, filter) => query.filter(q => iLike(q._2.name, filter)) // should replace with lucene
    }.run map toUserMember
    Page(values, page, pageSize, offset, total)
  }

  def get(id: MemberId)(implicit s: Session): Option[UserMember] = {
    members.filter(_._1.id === id).firstOption map toUserMember
  }

  def getEdit(id: MemberId)(implicit s: Session): Option[MemberFormData] = {
    get(id) map {um => MemberFormData(um.name, um.username, um.email, um.address, um.phone)}
  }

  def insert(data: MemberFormData)(implicit s: Session): MemberId = {
    s.withTransaction {
      val userId = userService.insert(UserEdit(data.name, data.username, Passwords.random()), UserDetailEdit(data.email, data.address, data.phone))
      val memberId = Members.autoInc insert MemberEdit(userId, archived = false)
      userService.addRole(userId, Roles.Member)
      memberId
    }
  }

  def update(id: MemberId, data: MemberFormData)(implicit s: Session) = {
   val updateQuery = for {
      m <- MembersQ if m.id is id
      u <- m.user
      d <- UserDetails if u.id is d.id
    } yield (Users.name ~ Users.username ~ UserDetails.email ~ UserDetails.address ~ UserDetails.phone) <>(MemberFormData.apply _, MemberFormData.unapply _)

    updateQuery.update(data)
  }

  def delete(id: MemberId)(implicit s: Session) = {
    MembersQ.filter(_.id === id).delete
  }
}
