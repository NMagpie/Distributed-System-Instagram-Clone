package routes

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import logging.LogHelper.{logError, logMessage}
import main.Main.{serviceManager, timeout}
import services.ServiceManager._
import services.authentication._

import scala.language.postfixOps
import scala.util.{Failure, Success}

object Login {

  import routes.Util._

  val login: Route = path("login") {

    headerValueByName("Authorization") {
      authData =>

        val authFuture = (serviceManager ? GetAuth).mapTo[AuthResult]

        onComplete(authFuture) {

          case Success(authResult) =>

            val authService = authResult.client

            logMessage(s"{login}\t$authData")

            val reply = call(authService, {
              authService.client.auth(UserData(authData))
            })

            serviceManager ! DecLoad(Left(authService))

            response(reply)

          case Failure(e) => logError(e)
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
        }

    }

  }

}
