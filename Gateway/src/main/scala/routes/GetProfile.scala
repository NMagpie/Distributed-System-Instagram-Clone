package routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import logging.LogHelper.{logError, logMessage}
import main.Main.system.dispatcher
import main.Main.{cacheServices, serviceManager, timeout}
import org.json4s.jackson.Serialization
import services.ServiceManager._
import services.cache._
import services.post._

import scala.language.postfixOps
import scala.util.{Failure, Success}

object GetProfile {

  import Util._

  val getProfile: Route = path("profile" / Segment) {
    username =>

      val query = call(cacheServices(0), {
        cacheServices(0).client.query(Query("get",
          s"{\"what\": \"getProfile\"," +
            s" \"of\": \"$username\"}"))
      })

      onComplete(query) {

        case Success(result) =>

          val isNull = result.message.equals("null")

          if (!isNull) {

            println("Reply sent")

            complete(HttpEntity(ContentTypes.`application/json`, result.message))

          } else {

            val postFuture = (serviceManager ? GetPost).mapTo[PostResult]

            onComplete(postFuture) {

              case Success(postResult) =>

                val postService = postResult.client

                logMessage(s"{getProfile}\t$username")

                val reply = call(postService, {
                  postService.client.getProfile(Username(username))
                })

                serviceManager ! DecLoad(Right(postService))

                reply.onComplete {
                  case Success(replyResult) =>

                    val postInfo = Profile(username, replyResult.name, replyResult.profilePicture, null)

                    call(cacheServices(0), {
                      cacheServices(0).client.query(Query("put", Serialization.write(postInfo)))
                    })
                }

                response(reply)

              case Failure(e) => logError(e)
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
            }

          }

        case Failure(e) => logError(e)
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
      }

  }

}
