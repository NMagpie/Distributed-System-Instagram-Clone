ThisBuild / version := "latest"

ThisBuild / scalaVersion := "2.13.8"

val akkaVersion = "2.6.19"

val akkaHttpVersion = "10.2.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "ch.qos.logback" % "logback-classic" % "1.4.1" % Runtime,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "org.json4s" %% "json4s-jackson" % "4.1.0-M1",
  "org.json4s" %% "json4s-native" % "4.1.0-M1",
  "com.github.cb372" %% "scalacache-guava" % "0.28.0",
)

enablePlugins(AkkaGrpcPlugin)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._

dockerBaseImage := "openjdk:18-alpine"

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "update"),
  ExecCmd("RUN", "apk", "add", "bash")
)

//dockerEnvVars := Map( "EXT_PORT"->"`docker inspect -f \"{{(index (index .NetworkSettings.Ports \"9005/tcp\")0).HostPort}}\" cache1`" )

dockerExposedPorts := {
  import com.typesafe.config.ConfigFactory

  val resourceDir = (resourceDirectory in Compile).value
  val appConfig = ConfigFactory.parseFile(resourceDir / "applicationDocker.conf")

  val config = ConfigFactory.load(appConfig)

  Seq(
  config.getInt("port")
  )
}

bashScriptExtraDefines += """addJava "-Dconfig.resource=applicationDocker.conf""""

lazy val root = (project in file("."))
  .settings(
    name := "Cache"
  )