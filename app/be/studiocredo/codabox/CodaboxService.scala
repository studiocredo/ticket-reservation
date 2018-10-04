package be.studiocredo.codabox

import be.studiocredo.Service
import be.studiocredo.codabox.CodaboxService.CodaboxInfo
import com.google.inject.Inject
import play.api.Play
import play.api.libs.json.{Format, Json, Reads}
import play.api.libs.ws.WS
import play.mvc.Http

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}


object Logger {
  val logger = play.api.Logger("be.studiocredo.codabox")

  def LogTry[A](computation: => A): Try[A] = {
    Try(computation) recoverWith {
      case e: Throwable =>
        logger.error("Failure", e)
        Failure(e)
    }
  }
}

object CodaboxService {
  object CodaboxInfo{
    implicit val codaBoxInfoFmt: Reads[CodaboxInfo] = Json.reads[CodaboxInfo]
  }
  case class CodaboxInfo(api_key: String, id: Long, name: String, status: CodaboxStatus)

  object CodaboxStatus{
    implicit val codaBoxStatusFmt: Reads[CodaboxStatus] = Json.reads[CodaboxStatus]
  }
  case class CodaboxStatus(processed: Long, skipped: Long, synced: Long, unprocessed: Long)

  object CodaboxAccountingItem {
    implicit val codaBoxAccountingItemFmt: Format[CodaboxAccountingItem] = Json.format[CodaboxAccountingItem]
  }

  case class CodaboxAccountingItem(name: String)

}


trait CodaboxService extends Service {
  def sync(): Future[Option[Int]]
  def info(): Future[Option[CodaboxInfo]]
}

class NullCodaboxService extends CodaboxService {
  override def sync(): Future[Option[Int]] = Future.apply(None)

  override def info(): Future[Option[CodaboxInfo]] = Future.apply(None)

  override def onStart(): Unit = {
    Logger.logger.debug("Starting mock codabox service")
  }
}

class RestCodaboxService @Inject()() extends CodaboxService {

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
        case Http.Status.OK => Logger.LogTry {
          (r.json \ "inserted").as[Int]
        }.toOption
        case _ => None
      }
    }
  }

  def info(): Future[Option[CodaboxInfo]] = {
    val url = WS.url(s"${configuration.get.url}/client/${configuration.get.client}")
    url.get().map { r =>
      r.status match {
        case Http.Status.OK => Logger.LogTry {
          r.json.as[CodaboxInfo]
        }.toOption
        case _ => None
      }
    }
  }
}
