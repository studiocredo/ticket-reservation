import be.studiocredo.{Service, Modules}
import be.studiocredo.util.AccessLog
import com.google.inject.{Stage, Guice}
import play.api._
import play.api.mvc._


object Global extends GlobalSettings {

  import Modules._

  // Not very scala like, but cake scares me too much still
  private lazy val injector = Guice.createInjector(stage, new BaseModule(), new AuthModule())
  private def stage = if (Play.current.mode == Mode.Prod) Stage.PRODUCTION else Stage.DEVELOPMENT
  override def getControllerInstance[A](controllerClass: Class[A]): A = injector.getInstance(controllerClass)


  def services: List[Service] = {
    import net.codingwell.scalaguice.InjectorExtensions._
    import scala.collection.JavaConverters._
    injector.instance[java.util.Set[Service]].asScala.toList
  }


  override def onStart(app: Application) = {
    services foreach (_.onStart())
  }
  override def onStop(app: Application) = {
    services foreach (_.onStop())
  }


  val filters = List(AccessLog)
  override def doFilter(a: EssentialAction): EssentialAction = {
    Filters(super.doFilter(a), filters: _*)
  }
}
