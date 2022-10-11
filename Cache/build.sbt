ThisBuild / version := "0.1.0-SNAPSHOT"

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
)

enablePlugins(AkkaGrpcPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "Cache"
  )
