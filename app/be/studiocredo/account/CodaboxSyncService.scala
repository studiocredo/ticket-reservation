package be.studiocredo.account

import akka.actor.Cancellable
import be.studiocredo.Service
import com.google.inject.Inject
import play.api.libs.concurrent.Akka

class CodaboxSyncService @Inject()(codaboxService: AccountStatementImportService) extends Service {
  var cancellable: Option[Cancellable] = None

  override def onStop() {
    cancellable.map(_.cancel())
    cancellable = None
  }

  override def onStart() {
    Logger.logger.info("Starting Codabox Sync service")
    import play.api.Play.current
    import play.api.libs.concurrent.Execution.Implicits._

    import scala.concurrent.duration._

    cancellable = Some(
      Akka.system.scheduler.schedule(0.seconds, 5.minutes) {
        codaboxService.sync().map { maybeQuantity =>
          maybeQuantity.filter(_ > 0).foreach(q => Logger.logger.info(s"$q payments synchronized"))
        }
      }
    )
  }
}