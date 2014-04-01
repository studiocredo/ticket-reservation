package be.studiocredo

import play.api.mvc.Request

trait BrowserDetectionSupport {

  val MobilePattern = "(iPhone|webOS|iPod|Android|BlackBerry|mobile|SAMSUNG|IEMobile|OperaMobi)".r.unanchored

  def isMobile[A](implicit request: Request[A]): Boolean = {
    request.headers.get("User-Agent").exists(agent => {
      agent match {
        case MobilePattern(a) => true
        case _ => false
      }
    })
  }

  val IEPattern = """MSIE (\d{1,}[\\.\d]{0,})""".r.unanchored

  def isOldExplorer[A](implicit request: Request[A]): Boolean = {
    request.headers.get("User-Agent").exists(agent => {
      agent match {
        case IEPattern(version) if version.toFloat < 9.0 => true
        case _ => false
      }
    })
  }

}
