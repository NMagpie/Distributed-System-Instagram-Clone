package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import cacheManager.CacheManager
import com.google.common.cache.{CacheBuilder, CacheLoader}
import services.cache.CacheServiceHandler
import main.Main.system.dispatcher

import scala.io.StdIn
import com.typesafe.config.ConfigFactory
import services.discovery.{DiscoveryService, DiscoveryServiceClient}
import services.ServiceInfo
import rpcImpl.RpcImpl
import taskLimiter.TlActor

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps

/*

  Microservice cache

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  implicit val system: ActorSystem = ActorSystem("my-system")

  val taskLimit: Int = ConfigFactory.load.getInt("taskLimit")

  val maxAge: Int = ConfigFactory.load.getInt("maxAge")

  val taskLimiter: ActorRef = system.actorOf(Props(new TlActor(taskLimit)), "taskLimiter")

  val cacheMng: ActorRef = system.actorOf(Props(new CacheManager(maxAge minutes)), "cacheManager")

  val hostname: String = ConfigFactory.load.getString("hostname")

  val port: Int = ConfigFactory.load.getInt("port")

  val discoveryHost: String = ConfigFactory.load.getString("discoveryHost")

  val discoveryPort: Int = ConfigFactory.load.getInt("discoveryPort")

  val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(discoveryHost, discoveryPort).withTls(false)

  val client: DiscoveryService = DiscoveryServiceClient(clientSettings)

  Await.result(client.discover(ServiceInfo("cache", hostname, port)), Duration.create(15, "min"))

  def main(args: Array[String]): Unit = {

    val bindServer = Http().newServerAt(hostname, port).bind(CacheServiceHandler(new RpcImpl))

    println(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine()
    bindServer
      .flatMap(_.unbind())
      .onComplete(_ => {
        system.terminate()
      })
  }

  def getCurrentTime: String = {
    val timestamp = LocalDateTime.now()
    DateTimeFormatter.ofPattern("HH:mm:ss").format(timestamp)
  }

}
