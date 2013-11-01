import sbt._
import sbt.Keys._
import play.Project
import play.Keys._

object ApplicationBuild extends Build {
  val appName = "ticket-reservation"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "com.typesafe.play" % "play-slick_2.10" % "0.5.0.8",

    "org.webjars" %% "webjars-play" % "2.2.0",
    "org.webjars" % "bootstrap" % "3.0.1",
    "org.webjars" % "jquery" % "2.0.3-1",
    "org.webjars" % "html5shiv" % "3.6.2",
    "org.webjars" % "select2" % "3.4.4",

    "org.postgresql" % "postgresql" % "9.2-1003-jdbc4",
    "com.github.tototoshi" %% "slick-joda-mapper" % "0.4.0",

    "com.chuusai" % "shapeless_2.10.2" % "2.0.0-M1", /* cross CrossVersion.full */

    play.Project.cache
  )

  val main = Project(appName, appVersion, appDependencies).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*).settings(
    organization := "be.studiocredo"
    , scalaVersion := "2.10.3"
    , scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")
  )
}
