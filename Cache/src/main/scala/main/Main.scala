package main

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.io.StdIn
import scala.util.{Failure, Success}
import com.typesafe.config.ConfigFactory

/*

  Microservice cache

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  implicit val system = ActorSystem(Behaviors.empty, "my-system")

  implicit val executionContext = system.executionContext

  def main(args: Array[String]): Unit = {

    val hostname = ConfigFactory.load.getString("hostname")

    val port = ConfigFactory.load.getInt("port")

    val bindServer = Http().newServerAt(hostname, port).bind(CacheServiceHandler(new RpcImpl))

    println(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine()
    bindServer
      .flatMap(_.unbind())
      .onComplete(_ => {
        system.terminate()
      })
  }

}
