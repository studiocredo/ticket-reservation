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

    "org.postgresql" % "postgresql" % "9.2-1003-jdbc4",
    "com.github.tototoshi" %% "slick-joda-mapper" % "0.4.0",

    play.Project.cache
  )


  val main = Project(appName, appVersion, appDependencies).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*).settings(
    organization := "be.studiocredo",
    scalaVersion := "2.10.2"
  ).dependsOn(RootProject(uri("git://github.com/freekh/play-slick.git"))) // scary shit

}
