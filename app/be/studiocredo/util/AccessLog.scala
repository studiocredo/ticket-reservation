package be.studiocredo.util

import controllers.routes
import play.api._
import play.api.mvc._
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object AccessLog extends Filter {
  val logger = Logger("be.studiocredo.requests")

  def isAssetRequest(request: RequestHeader): Boolean = {
    val controller = request.tags.get(play.api.Routes.ROUTE_CONTROLLER)
    if (controller.exists(_.endsWith("Assets")))
      true
    else
      request.uri.equals(routes.Application.javascriptRoutes().url)
  }

  def format(result: SimpleResult, duration: Long, request: RequestHeader): String = {
    s"${result.header.status} - ${duration}ms - ${request.method} ${request.uri}"
  }

  def apply(next: (RequestHeader) => Future[SimpleResult])(request: RequestHeader): Future[SimpleResult] = {
    val startTime = System.nanoTime
    val asset = isAssetRequest(request)

    next(request).map { result =>
      val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - startTime)
      if (asset) {
        if (logger.isDebugEnabled)
          logger.debug(format(result, duration, request))
      } else {
        if (logger.isInfoEnabled)
          logger.info(format(result, duration, request))
      }
      result.withHeaders("Request-Time" -> duration.toString)
    }
  }

}
