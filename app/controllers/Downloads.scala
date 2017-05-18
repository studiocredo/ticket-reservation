package controllers

import be.studiocredo._
import be.studiocredo.auth.{AuthenticatorService, Authorization, Secure}
import be.studiocredo.aws.DownloadService
import com.google.inject.Inject
import models.ids.{AssetId, UserId}
import play.api.Logger
import play.api.mvc.Controller


class Downloads @Inject()(assetService: AssetService, downloadService: DownloadService, val authService: AuthenticatorService, val notificationService: NotificationService, val userService: UserService) extends Controller with Secure with UserContextSupport {
  val logger = Logger("be.studiocredo.orders")

  val defaultAuthorization = Some(Authorization.ANY)

  def download(assetId: AssetId) = AuthDBAction { implicit rs =>
    val user = rs.user.user.user
    assetService.get(assetId).fold(NotFound("GVD")) { asset =>
      downloadService.getDownloadUrl(asset, user).fold(InternalServerError("GVD")) { url =>
        Redirect(url)
      }
    }
  }
}
