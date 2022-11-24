package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import logging.LogHelper.{logError, logMessage}
import main.Main.system.dispatcher
import org.json4s.jackson.Serialization
import org.json4s.{FieldSerializer, Formats, NoTypeHints}
import routes.GetPosts.getPosts
import routes.GetProfile.getProfile
import routes.GetStatus.getStatus
import routes.Login.login
import routes.PutPost.putPost
import routes.Register.register
import rpcImpl.RpcImpl
import scalapb.GeneratedMessage
import services.Services.{AuthService, CacheService, PostService}
import services.discovery._
import services.gateway._
import services.{ServiceInfo, ServiceManager}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.io.StdIn
import scala.language.postfixOps

/*

  GATEWAY API

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  implicit val system: ActorSystem = ActorSystem("my-system")

  implicit val formats: Formats = Serialization.formats(NoTypeHints) + FieldSerializer[GeneratedMessage]()

  val hostname: String = ConfigFactory.load.getString("hostname")

  val httpPort: Int = ConfigFactory.load.getInt("httpPort")

  val grpcPort: Int = ConfigFactory.load.getInt("grpcPort")

  val discoveryHost: String = ConfigFactory.load.getString("discoveryHost")

  val discoveryPort: Int = ConfigFactory.load.getInt("discoveryPort")

  val clientSettings: GrpcClientSettings = GrpcClientSettings
    .connectToServiceAt(discoveryHost, discoveryPort)
    .withTls(false)
    //.withConnectionAttempts(10)

  val discovery: DiscoveryService = DiscoveryServiceClient(clientSettings)

  var authServices = Array.empty[AuthService]

  var postServices = Array.empty[PostService]

  var cacheServices = Array.empty[CacheService]

    try {

      val serviceMap = Await.result(discovery.discover(ServiceInfo("gateway", hostname, grpcPort)), Duration.create(15, "min"))

      val authServices = serviceMap.auth.map(service => AuthService(service.`type`, service.hostname, service.port)).toArray

      val postServices = serviceMap.post.map(service => PostService(service.`type`, service.hostname, service.port)).toArray

      val cacheServices = serviceMap.cache.map(service => CacheService(service.`type`, service.hostname, service.port)).toArray

      this.authServices = authServices

      this.postServices = postServices

      this.cacheServices = cacheServices

    } catch {
      case e: Exception => logError(e)
    }

  val serviceManager: ActorRef = system.actorOf(Props(new ServiceManager()), "serviceManager")

  implicit val timeout: Timeout = Timeout(10 seconds)

  def main(args: Array[String]): Unit = {

    val bindServer = Http().newServerAt(hostname, grpcPort).bind(GatewayServiceHandler(new RpcImpl))

    val route = {
      register ~ login ~ getPosts ~ getProfile ~ putPost ~ getStatus
    }

    val bindingFuture = Http().newServerAt(hostname, httpPort).bind(route)

    logMessage("Server now online.\nPress RETURN to stop...")

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

    bindServer
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

  }

}