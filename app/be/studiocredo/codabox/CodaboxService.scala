package be.studiocredo.codabox

import be.studiocredo.Service
import com.google.inject.Inject
import play.api.Play
import play.api.libs.ws.WS
import play.mvc.Http

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object Logger {
  val logger = play.api.Logger("be.studiocredo.codabox")
}

class CodaboxService @Inject()() extends Service {

  var configuration: Option[CodaboxConfiguration] = None

  override def onStart() {
    Logger.logger.debug("Starting codabox service")
    configuration = CodaboxConfiguration.init(Play.current.configuration)
  }

  override def onStop(): Unit = {
    Logger.logger.debug("Stopping codabox service")
  }

  def sync(): Future[Option[Int]] = {
    val url = WS.url(s"${configuration.get.url}/sync/${configuration.get.client}/account_movement")
    url.delete().map { r =>
      r.status match {
        case Http.Status.OK => Try {
          (r.json \ "inserted").as[Int]
        }.toOption
        case _ => None
      }
    }
  }
}
