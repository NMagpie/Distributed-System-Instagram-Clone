package routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import logging.LogHelper.logMessage
import main.Main.system.dispatcher
import main.Main.{serviceManager, timeout}
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

      val cache = randomCache()

      val query = call(cache, {
        cache.client.query(Query("get",
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

                decLoad(_, postService)

                reply.onComplete {
                  case Success(replyResult) =>

                    val postInfo = Profile(username, replyResult.name, replyResult.profilePicture, null)

                    call(cache, {
                      cache.client.query(Query("put", Serialization.write(postInfo)))
                    })
                }

                response(Left(reply))

              case Failure(e) => response(Right(e))
            }

          }

        case Failure(e) => response(Right(e))
      }

  }

}
