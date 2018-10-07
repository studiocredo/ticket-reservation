package be.studiocredo.account

import java.io.File

import be.studiocredo.Service
import be.studiocredo.account.AccountStatementImportService.CodaboxSyncStatus.CodaboxSyncStatus
import be.studiocredo.account.AccountStatementImportService._
import be.studiocredo.util.Money
import com.google.inject.Inject
import models.entities.{Payment, PaymentEdit, PaymentType}
import org.joda.time.LocalDate
import play.api.Play
import play.api.libs.json._
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

  object CodaboxInfo {
    implicit val codaBoxInfoFmt: Format[CodaboxInfo] = Json.format[CodaboxInfo]
  }

  case class CodaboxInfo(api_key: String, id: Long, name: String, status: CodaboxStatus)

  object CodaboxStatus {
    implicit val codaBoxStatusFmt: Format[CodaboxStatus] = Json.format[CodaboxStatus]
  }

  case class CodaboxStatus(processed: Long, skipped: Long, synced: Long, unprocessed: Long)

  object CodaboxSyncResponse {
    implicit val codaboxSyncResponseFmt: Format[CodaboxSyncResponse] = Json.format[CodaboxSyncResponse]
  }

  case class CodaboxSyncResponse(updated: Long, failed: Long)

  object CodaboxProcessStatus {
    implicit val codaboxProcessStatusFmt: Format[CodaboxProcessStatus] = Json.format[CodaboxProcessStatus]
  }

  implicit val codaboxProcessStatusFmt: Format[CodaboxProcessStatus] = Json.format[CodaboxProcessStatus]

  case class CodaboxProcessStatus(id: Long, status: CodaboxSyncStatus)

  object CodaboxAccountMovement {
    implicit val codaBoxAccountMovementItemFmt: Format[CodaboxAccountMovement] = Json.format[CodaboxAccountMovement]
  }

  case class CodaboxAccountMovement(
                                     amount: Long,
                                     archived: Boolean,
                                     coda_sequence: String,
                                     codabox_feed_index: String,
                                     counterparty_account: String,
                                     counterparty_bic: String,
                                     counterparty_locality: String,
                                     counterparty_name: String,
                                     counterparty_street: String,
                                     description: String,
                                     holder_account: String,
                                     holder_bic: String,
                                     id: Long,
                                     movement_sequence: String,
                                     statement_creation_date: LocalDate,
                                     structured_reference: Option[String],
                                     value_date: LocalDate
                                   )

  object CodaboxSyncStatus extends Enumeration {
    type CodaboxSyncStatus = Value
    val Processed: CodaboxSyncStatus.Value = Value("processed")
    val Skipped: CodaboxSyncStatus.Value = Value("skipped")

    implicit val codaboxSyncStatusFmt: Format[CodaboxSyncStatus] = new Format[CodaboxSyncStatus] {
      def reads(json: JsValue) = JsSuccess(CodaboxSyncStatus.withName(json.as[String]))

      def writes(myEnum: CodaboxSyncStatus) = JsString(myEnum.toString)
    }
  }

}


trait AccountStatementImportService extends Service {
  def sync(): Future[Option[Int]]

  def info(): Future[Option[CodaboxInfo]]

  def update(payments: Seq[Payment], status: CodaboxSyncStatus): Future[Option[CodaboxSyncResponse]]

  def extract(file: Option[File]): Future[Seq[PaymentEdit]]

  val upload: Boolean = false
}

class NullAccountStatementImportService extends AccountStatementImportService {
  override def sync(): Future[Option[Int]] = Future.apply(None)

  override def info(): Future[Option[CodaboxInfo]] = Future.apply(None)

  override def extract(file: Option[File]): Future[Seq[PaymentEdit]] = Future.apply(Nil)

  override def update(payments: Seq[Payment], status: CodaboxSyncStatus): Future[Option[CodaboxSyncResponse]] = Future.apply(None)

  override def onStart(): Unit = {
    Logger.logger.debug("Starting mock account statement import service")
  }
}

class UploadAccountStatementImportService @Inject()(transactionImporter: TransactionImporter) extends AccountStatementImportService {
  override val upload: Boolean = true

  override def sync(): Future[Option[Int]] = Future.apply(None)

  override def info(): Future[Option[CodaboxInfo]] = Future.apply(None)

  override def extract(file: Option[File]): Future[Seq[PaymentEdit]] = Future.apply(file.map(transactionImporter.importFile).getOrElse(Nil))

  override def update(payments: Seq[Payment], status: CodaboxSyncStatus): Future[Option[CodaboxSyncResponse]] = Future.apply(None)

  override def onStart(): Unit = {
    Logger.logger.debug("Starting upload account statement import service")
  }
}

class CodaboxAccountStatementImportService @Inject()() extends AccountStatementImportService {

  var configuration: CodaboxConfiguration = _

  override def onStart() {
    Logger.logger.debug("Starting codabox account statement import service")
    configuration = CodaboxConfiguration.init(Play.current.configuration)
  }

  override def onStop(): Unit = {
    Logger.logger.debug("Stopping account statement import service")
  }

  override def sync(): Future[Option[Int]] = {
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

  override def info(): Future[Option[CodaboxInfo]] = {
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

  override def extract(file: Option[File] = None): Future[Seq[PaymentEdit]] = sync().flatMap(getAccountMovementsRec)

  override def update(payments: Seq[Payment], status: CodaboxSyncStatus): Future[Option[CodaboxSyncResponse]] = {
    val url = WS.url(s"${configuration.url}/sync/${configuration.client}/account_movement")
    val body = payments.flatMap { payment =>
      payment.importId.flatMap{ importId =>
        Try {
          importId.toLong
        }.toOption
      }.map(CodaboxProcessStatus(_, status))
    }
    url.put(Json.toJson(body)).map { r =>
      r.status match {
        case Http.Status.OK => Logger.LogTry {
          r.json.as[CodaboxSyncResponse]
        }.toOption
        case _ => None
      }
    }
  }


  private def getAccountMovementsRec(maybePage: Option[Int] = Some(0)): Future[Seq[PaymentEdit]] = {
    maybePage.map { page =>
      val url = WS.url(s"${configuration.url}/client/${configuration.client}/account_movements?p=$page")
      val futurePaymentEdits = url.get().map { r =>
        val accountMovements = r.status match {
          case Http.Status.OK => Logger.LogTry {
            (r.json \ "items").as[Seq[CodaboxAccountMovement]]
          }.getOrElse(Nil)
          case _ => Nil
        }
        val next = if (accountMovements.nonEmpty) (r.json \ "next").as[Option[Int]].filter(_ < 50) else None //Max recursion limit
        (accountMovements.map(toPaymentItemEdit), next)
      }
      futurePaymentEdits.flatMap {
        case (paymentEdits, next) => getAccountMovementsRec(next).map(paymentEdits ++ _)
      }
    }.getOrElse(Future.successful(Nil))
  }

  def toPaymentItemEdit(codaboxAccountMovement: CodaboxAccountMovement): PaymentEdit = {
    def getMessage = {
      Some(codaboxAccountMovement.structured_reference.getOrElse(codaboxAccountMovement.description))
    }

    def getDetails = {
      if (codaboxAccountMovement.amount < 0) {
        Some(s"Overschrijving naar rekeningnummer ${codaboxAccountMovement.counterparty_account} " +
          s"van ${codaboxAccountMovement.counterparty_name}, " +
          s"${Seq(codaboxAccountMovement.counterparty_street, codaboxAccountMovement.counterparty_locality).mkString}"
        )
      } else {
        Some(s"Overschrijving van rekeningnummer ${codaboxAccountMovement.counterparty_account} " +
          s"vanwege ${codaboxAccountMovement.counterparty_name}, " +
          s"${Seq(codaboxAccountMovement.counterparty_street, codaboxAccountMovement.counterparty_locality).mkString}"
        )
      }
    }

    PaymentEdit(
      PaymentType.OnlineTransaction,
      Some(codaboxAccountMovement.id.toString),
      None,
      codaboxAccountMovement.counterparty_name,
      Money(codaboxAccountMovement.amount / 1000.0),
      getMessage,
      getDetails,
      codaboxAccountMovement.value_date.toDateTimeAtStartOfDay,
      archived = false
    )
  }
}
