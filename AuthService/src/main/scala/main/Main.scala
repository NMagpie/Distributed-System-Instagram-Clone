package main

import rpcImpl.RpcImpl
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import authentication.AuthenticationServiceHandler
import com.typesafe.config.ConfigFactory

import scala.io.StdIn
import db.DBConnector

/*

  Authentication service

  MADE BY: ДОДОН ИГОРЬ, ПАРЛАМЕНТУЛ РЕПУБЛИЧИЙ МОЛДОВА

 */

object Main {

  implicit val system = ActorSystem(Behaviors.empty, "my-system")

  implicit val executionContext = system.executionContext

  def main(args: Array[String]): Unit = {

    val hostname = ConfigFactory.load.getString("hostname")

    val port = ConfigFactory.load.getInt("port")

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

}