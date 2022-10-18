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
  "org.json4s" %% "json4s-jackson" % "4.1.0-M1",
  "org.json4s" %% "json4s-native" % "4.1.0-M1",
  "ch.qos.logback" % "logback-classic" % "1.4.1" % Runtime,
  "mysql" % "mysql-connector-java" % "8.0.30",
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
)

enablePlugins(AkkaGrpcPlugin)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "AuthService"
  )

dockerBaseImage := "openjdk:jre"