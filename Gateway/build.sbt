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
  "org.json4s" %% "json4s-jackson" % "4.1.0-M1",
  "org.json4s" %% "json4s-native" % "4.1.0-M1",
  "org.apache.logging.log4j" % "log4j-layout-template-json" % "2.19.0" % "runtime",
  "org.apache.logging.log4j" %% "log4j-api-scala" % "12.0",
  "org.apache.logging.log4j" % "log4j-api" % "2.19.0",
  "org.apache.logging.log4j" % "log4j-core" % "2.19.0" % Runtime,
  "ch.qos.logback" % "logback-classic" % "1.4.1" % Runtime,
  "mysql" % "mysql-connector-java" % "5.1.24",
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.0",
)

enablePlugins(AkkaGrpcPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "Gateway"
  )
