package be.studiocredo.codabox

import play.api.Configuration

import scala.util.{Failure, Try}

object CodaboxConfigurationKeys {
  val url: String = "codabox.url"
  val client: String = "codabox.client"
}

object CodaboxConfiguration {
  def init(configuration: Configuration): Option[CodaboxConfiguration] = {
    Try(new CodaboxConfiguration(configuration)).recoverWith { case t: Throwable =>
      Logger.logger.error("Failed to initialize codabox configuration", t)
      Failure(t)
    }.toOption
  }
}

class CodaboxConfiguration(val configuration: Configuration) {
  val url: String = configuration.getString(CodaboxConfigurationKeys.url).get
  val client: String = configuration.getString(CodaboxConfigurationKeys.client).get
}

