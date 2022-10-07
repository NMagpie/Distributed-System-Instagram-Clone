package main

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import authentication.AuthenticationServiceHandler
import com.typesafe.config.ConfigFactory
import db.DBConnector
import discovery._
import rpcImpl.RpcImpl

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn

/*

  Authentication service

  MADE BY: ДОДОН ИГОРЬ, ПАРЛАМЕНТУЛ РЕПУБЛИЧИЙ МОЛДОВА

 */

object Main {

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "my-system")

  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  val hostname: String = ConfigFactory.load.getString("hostname")

  val port: Int = ConfigFactory.load.getInt("port")

  val discoveryHost: String = ConfigFactory.load.getString("discoveryHost")

  val discoveryPort: Int = ConfigFactory.load.getInt("discoveryPort")

  val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(discoveryHost, discoveryPort).withTls(false)

  val client: DiscoveryService = DiscoveryServiceClient(clientSettings)

  Await.ready(client.discover(ServiceInfo("auth", hostname, port)), Duration.create(15, "min"))

  def main(args: Array[String]): Unit = {

    val bindServer = Http().newServerAt(hostname, port).bind(AuthenticationServiceHandler(new RpcImpl))

    println(s"Server now online.\nPress RETURN to stop...")
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