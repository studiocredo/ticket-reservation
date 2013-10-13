import play.api.{Application, GlobalSettings}

object Global extends GlobalSettings {

  override def beforeStart(app: Application): Unit = super.beforeStart(app)
  override def onStart(app: Application) = super.onStart(app)
  override def onStop(app: Application): Unit = super.onStop(app)

  override def getControllerInstance[A](controllerClass: Class[A]): A = super.getControllerInstance(controllerClass)
}
