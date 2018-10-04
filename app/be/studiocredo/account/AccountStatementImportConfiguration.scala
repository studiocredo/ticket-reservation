package be.studiocredo.account

import play.api.Configuration

import scala.util.{Failure, Try}

object AccountStatementImportConfigurationKeys {
  val codaboxUrl: String = "account.import.codabox.url"
  val codaboxClient: String = "account.import.codabox.client"
  val uploadType: String = "account.import.upload.type"
}

object CodaboxConfiguration {
  def init(configuration: Configuration): CodaboxConfiguration = {
    Try(new CodaboxConfiguration(configuration)).recoverWith { case t: Throwable =>
      Logger.logger.error("Failed to initialize codabox configuration", t)
      Failure(t)
    }.get
  }
}

class CodaboxConfiguration(val configuration: Configuration) {
  val url: String = configuration.getString(AccountStatementImportConfigurationKeys.codaboxUrl).get
  val client: String = configuration.getString(AccountStatementImportConfigurationKeys.codaboxClient).get
}


