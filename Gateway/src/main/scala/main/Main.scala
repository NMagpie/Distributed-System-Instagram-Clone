package main

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.dispatch.Futures.future
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.io.StdIn
import scala.util.{Failure, Success}
import authentication._
import com.typesafe.config.ConfigFactory

/*

  GATEWAY API

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  implicit val system = ActorSystem(Behaviors.empty, "my-system")

  implicit val executionContext = system.executionContext

  def main(args: Array[String]): Unit = {

    val hostname = ConfigFactory.load.getString("hostname")

    val port = ConfigFactory.load.getInt("port")

    val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 9001).withTls(false)

    val client: AuthenticationService = AuthenticationServiceClient(clientSettings)

    val route = {

      path("register") {
        headerValueByName("Authorization") {
          authData =>
            val reply = client.register(UserData(authData))
            onComplete(reply) {
              case Success(replyResult) => println("Reply sent")
                complete(HttpEntity(ContentTypes.`application/json`, replyResult.toProtoString))
              case Failure(e) => e.printStackTrace()
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
            }
        }
      } ~ path("login") {
        headerValueByName("Authorization") {
          authData =>
            val reply = client.auth(UserData(authData))
            onComplete(reply) {
              case Success(replyResult) => println("Reply sent")
                complete(HttpEntity(ContentTypes.`application/json`, replyResult.toProtoString))
              case Failure(e) => e.printStackTrace()
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
            }
        }
      }
    }

    val bindingFuture = Http().newServerAt(hostname, port).bind(route)

    println(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

  }

}
