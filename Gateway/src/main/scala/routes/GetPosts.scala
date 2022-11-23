package routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import logging.LogHelper.{logError, logMessage}
import main.Main.system.dispatcher
import main.Main.{cacheServices, postServices, serviceManager, timeout}
import org.json4s.jackson.Serialization
import services.ServiceManager._
import services.cache.Query
import services.post.PostParams

import scala.language.postfixOps
import scala.util.{Failure, Success}

object GetPosts {

  import routes.Util._

  val getPosts: Route = pathPrefix("profile" / Segment / IntNumber) {
    case (username, dozen) =>

      val query = call(cacheServices(0), {
        cacheServices(0).client.query(Query("get",
          s"{\"what\": \"getPost\"," +
            s" \"of\": \"$username\"," +
            s"\"dozen\": \"$dozen\"}"))
      })

      onComplete(query) {

        case Success(result) =>

          val isNull = result.message.equals("null")

          if (!isNull) {

            println("Reply sent")

            complete(HttpEntity(ContentTypes.`application/json`, "{ \"postInfo\": " + result.message + "}"))

          } else {

            val postFuture = (serviceManager ? GetPost).mapTo[PostResult]

            onComplete(postFuture) {

              case Success(postResult) =>

                logMessage(s"{getPosts}\t$username\t$dozen")

                val postService = postResult.client

                val reply = call(postService, {
                  postService.client.getPost(PostParams(username, dozen))
                })

                decLoad(_, postService)

                reply.onComplete {
                  case Success(replyResult) =>

                    val postInfo = Profile(username, null, null, Map(s"$dozen" -> replyResult.postInfo.toArray))

                    call(cacheServices(0), {
                      cacheServices(0).client.query(Query("put", Serialization.write(postInfo)))
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
