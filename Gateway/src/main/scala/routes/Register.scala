package routes

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.google.protobuf.{ByteString => pByteString}
import logging.LogHelper.{logError, logMessage}
import main.Main.{serviceManager, timeout}
import services.ServiceManager._
import services.authentication._
import services.post._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Register {

  import routes.Util._

  val register: Route = post {

    path("register") {
      headerValueByName("Authorization") {
        authData =>

          val authFuture = (serviceManager ? GetAuth).mapTo[AuthResult]

          val username = new String(Base64.getDecoder.decode(authData), StandardCharsets.UTF_8).split(':')(0)

          logMessage(s"{register}\t$username")
          onComplete(authFuture) {

            case Success(authResult) =>

              val authService = authResult.client

              val reply = call(authService, {
                authService.client.register(UserData(authData))
              })

              onComplete(reply) {
                case Success(replyResult) =>
                  if (replyResult.key.isDefined) {

                    formFields("name") {
                      name =>
                        fileUpload("photo") {
                          case (metaData, file) =>

                            val postFuture = (serviceManager ? GetPost).mapTo[PostResult]

                            onComplete(postFuture) {
                              case Success(postResult) =>

                                val postService = postResult.client

                                val fileType = "." + metaData.getContentType.mediaType.subType

                                if (!checkFileType(fileType) || file == null) {

                                  val reply = call(postService, {
                                    postService.client.putProfile(ProfilePutInfo(username, name))
                                  })
                                  response(reply)

                                }

                                val otherChunk = Flow[ByteString].map(i => PictureInfo(pByteString.copyFrom(i.toArray), None))

                                val eof = Flow[Seq[Byte]].map(i => PictureInfo(pByteString.copyFrom(i.toArray), Option(fileType)))

                                val photo = file.mapConcat(chunk => chunk.grouped(1024))
                                  .mapMaterializedValue(_ => NotUsed)
                                  .via(otherChunk)
                                  .concat(Source.single("1".getBytes().toSeq).via(eof))

                                onComplete(call(postService, {
                                  postService.client.putPicture(photo)
                                })) {

                                  case Success(result) =>
                                    val reply = call(postService, {
                                      postService.client.putProfile(ProfilePutInfo(username, name, result.link.get))
                                    })
                                    serviceManager ! DecLoad(Left(authService))
                                    serviceManager ! DecLoad(Right(postService))
                                    response(reply)

                                  case Failure(e) => logError(e)
                                    serviceManager ! DecLoad(Left(authService))
                                    serviceManager ! DecLoad(Right(postService))
                                    complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                                }

                              case Failure(e) => logError(e)
                                serviceManager ! DecLoad(Left(authService))
                                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                            }
                        }
                    }
                  } else {
                    serviceManager ! DecLoad(Left(authService))
                    complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, replyResult.toString))
                  }

                case Failure(e) => logError(e)
                  serviceManager ! DecLoad(Left(authService))
                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
              }

            case Failure(e) => logError(e)
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
          }
      }
    }
  }

}
