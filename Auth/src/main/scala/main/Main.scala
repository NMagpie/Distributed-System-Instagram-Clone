package main

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import authentication.AuthenticationServiceHandler
import com.typesafe.config.ConfigFactory
import db.DBConnector
import discovery._
import rpcImpl.RpcImpl
import taskLimiter.tlActor

import main.Main.system.dispatcher

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.io.StdIn

/*

  Authentication service

  MADE BY: ДОДОН ИГОРЬ, ПАРЛАМЕНТУЛ РЕПУБЛИЧИЙ МОЛДОВА

 */

object Main {

  implicit val system: ActorSystem = ActorSystem("my-system")

  val taskLimiter: ActorRef = system.actorOf(Props(new tlActor(15)), "taskLimiter")

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