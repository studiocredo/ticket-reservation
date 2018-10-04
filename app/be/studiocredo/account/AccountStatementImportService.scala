package be.studiocredo.account

import be.studiocredo.Service
import be.studiocredo.account.AccountStatementImportService.CodaboxInfo
import com.google.inject.Inject
import play.api.Play
import play.api.libs.json.{Format, Json, Reads}
import play.api.libs.ws.WS
import play.mvc.Http

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}


object Logger {
  val logger = play.api.Logger("be.studiocredo.account")

  def LogTry[A](computation: => A): Try[A] = {
    Try(computation) recoverWith {
      case e: Throwable =>
        logger.error("Failure", e)
        Failure(e)
    }
  }
}

object AccountStatementImportService {
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


trait AccountStatementImportService extends Service {
  def sync(): Future[Option[Int]]
  def info(): Future[Option[CodaboxInfo]]
}

class NullAccountStatementImportService extends AccountStatementImportService {
  override def sync(): Future[Option[Int]] = Future.apply(None)

  override def info(): Future[Option[CodaboxInfo]] = Future.apply(None)

  override def onStart(): Unit = {
    Logger.logger.debug("Starting mock account statement import service")
  }
}

class UploadAccountStatementImportService extends AccountStatementImportService {
  override def sync(): Future[Option[Int]] = Future.apply(None)

  override def info(): Future[Option[CodaboxInfo]] = Future.apply(None)

  override def onStart(): Unit = {
    Logger.logger.debug("Starting upload account statement import service")
  }
}

class CodaboxAccountStatementImportService @Inject()() extends AccountStatementImportService {

  var configuration: CodaboxConfiguration = _

  override def onStart() {
    Logger.logger.debug("Starting account statement import service")
    configuration = CodaboxConfiguration.init(Play.current.configuration)
  }

  override def onStop(): Unit = {
    Logger.logger.debug("Stopping account statement import service")
  }

  def sync(): Future[Option[Int]] = {
    val url = WS.url(s"${configuration.url}/sync/${configuration.client}/account_movement")
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
    val url = WS.url(s"${configuration.url}/client/${configuration.client}")
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
