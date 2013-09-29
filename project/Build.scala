import sbt._
import Keys._
import play.Project

object ApplicationBuild extends Build {

  val appName = "ticket-reservation"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "org.webjars" %% "webjars-play" % "2.2.0",
    "org.webjars" % "bootstrap" % "3.0.0",
    "org.webjars" % "html5shiv" % "3.6.2",

    play.Project.cache,
    play.Project.jdbc
  )


  val main = Project(appName, appVersion, appDependencies).settings(
    organization := "be.studiocredo"
  )

}
