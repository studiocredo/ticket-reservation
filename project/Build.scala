import sbt._
import sbt.Keys._
import play.Project
import play.Keys._

object ApplicationBuild extends Build {
  val appName = "ticket-reservation"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "org.webjars" %% "webjars-play" % "2.2.0",
    "org.webjars" % "bootstrap" % "3.0.3",
    "org.webjars" % "jquery" % "2.0.3-1",
    "org.webjars" % "jquery-ui" % "1.10.3",
    "org.webjars" % "jquery-ui-themes" % "1.10.3",
    "org.webjars" % "html5shiv" % "3.6.2",
    "org.webjars" % "select2" % "3.4.4",
    "org.webjars" % "angularjs" % "1.2.1",
    "org.webjars" % "angular-ui-bootstrap" % "0.10.0",
    "org.webjars" % "requirejs" % "2.1.8",

    "org.postgresql" % "postgresql" % "9.2-1003-jdbc4",
    "com.github.tototoshi" %% "slick-joda-mapper" % "0.4.0",

    "org.joda" % "joda-money" % "0.9",

    "com.google.inject" % "guice" % "3.0",
    "net.codingwell" % "scala-guice_2.10" % "3.0.2",

    play.Project.cache,
    "com.typesafe.play" % "play-slick_2.10" % "0.5.0.8" exclude("com.jolbox", "bonecp"),
    "com.jolbox" % "bonecp" % "0.8.0.RELEASE",

    "org.mindrot" % "jbcrypt" % "0.3m",

    "org.apache.commons" % "commons-email" % "1.3.1",
    "com.typesafe" % "play-plugins-util_2.10" % "2.2.0" notTransitive(),
    "com.typesafe" % "play-plugins-mailer_2.10" % "2.2.0" notTransitive()
  )

  val main = Project(appName, appVersion, appDependencies).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*).settings(
    organization := "be.studiocredo"
    , scalaVersion := "2.10.3"
    , scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")
    , routesImport += "models.ids._"
    , routesImport += "models.ids.TypedId._"
    , resolvers += Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
    , initialCommands in console := scala.io.Source.fromFile("./scripts/play-console.scala").mkString
  )
}
