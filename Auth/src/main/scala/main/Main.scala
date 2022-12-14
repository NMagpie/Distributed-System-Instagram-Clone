package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import caching.CacheActor
import services.authentication.{AuthenticationServiceHandler, RegisterData, UserData}
import com.typesafe.config.ConfigFactory
import db.DBConnector
import logging.LogHelper.logMessage
import services.discovery._
import services.ServiceInfo
import rpcImpl.RpcImpl
import taskLimiter.TlActor
import main.Main.system.dispatcher

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.io.StdIn

/*

  Authentication service

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  implicit val system: ActorSystem = ActorSystem("my-system")

  val taskLimiter: ActorRef = system.actorOf(Props(new TlActor(15)), "taskLimiter")

  val cacheActor: ActorRef = system.actorOf(Props[CacheActor], "cache")

  var cache: Map[Int, RegisterData] = Map.empty

  val hostname: String = ConfigFactory.load.getString("hostname")

  val port: Int = ConfigFactory.load.getInt("port")

  val discoveryHost: String = ConfigFactory.load.getString("discoveryHost")

  val discoveryPort: Int = ConfigFactory.load.getInt("discoveryPort")

  val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(discoveryHost, discoveryPort).withTls(false)

  val client: DiscoveryService = DiscoveryServiceClient(clientSettings)

  Await.ready(client.discover(ServiceInfo("auth", hostname, port)), Duration.create(15, "min"))

  def main(args: Array[String]): Unit = {

    val bindServer = Http().newServerAt(hostname, port).bind(AuthenticationServiceHandler(new RpcImpl))

    logMessage(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine()
    bindServer
      .flatMap(_.unbind())
      .onComplete(_ => {
        DBConnector.closeConnection()
        system.terminate()
      })
  }

  def getCurrentTime: String = {
    val timestamp = LocalDateTime.now()
    DateTimeFormatter.ofPattern("HH:mm:ss").format(timestamp)
  }

}