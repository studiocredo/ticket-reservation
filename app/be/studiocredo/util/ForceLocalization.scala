import play.api._
import play.api.i18n.Lang
import play.api.mvc._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current

//based on http://stackoverflow.com/questions/21056304/play-framework-2-2-0-force-the-language-using-filter-and-global-object
object ForceLocalization extends Filter {

  def apply(next: (RequestHeader) => Future[SimpleResult])(request: RequestHeader): Future[SimpleResult] = {
    val applicationLangs = Play.current.configuration.getString("application.langs")
    next(request).map { sr =>
      applicationLangs match {
        case Some(langs) => withLang(sr, langs)
        case None => sr
      }
    }
  }

  private def withLang(sr:SimpleResult, langs:String) = {
    sr.withCookies(Cookie(Play.langCookieName, langs))
  }

}