package routes

import akka.NotUsed
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.google.protobuf.{ByteString => pByteString}
import logging.LogHelper.logMessage
import main.Main.{serviceManager, timeout}
import services.ServiceManager._
import services.authentication.AuthKey
import services.post.{PictureInfo, PostPutInfo}

import scala.language.postfixOps
import scala.util.{Failure, Success}

object PutPost {

  import Util._

  val putPost: Route = post {
    path("upload") {
      headerValueByName("Key") {
        key =>
          val authFuture = (serviceManager ? GetAuth).mapTo[AuthResult]

          onComplete(authFuture) {
            case Success(authResult) =>

              val authService = authResult.client

              val reply = call(authService, {
                authService.client.whoIsThis(AuthKey(key))
              })

              onComplete(reply) {
                case Success(usernameM) =>

                  val username = usernameM.username

                  formFields("text") {
                    text =>

                      logMessage(s"{putPost}\t$username\t$text")

                      fileUpload("photo") {
                        case (metaData, file) =>

                          val fileType = "." + metaData.getContentType.mediaType.subType

                          if (!checkFileType(fileType))
                            complete("Image has to have only one of these file types: .png, .jpg, .jpeg")

                          val otherChunk = Flow[ByteString].map(i => PictureInfo(pByteString.copyFrom(i.toArray), None))

                          val eof = Flow[Seq[Byte]].map(i => PictureInfo(pByteString.copyFrom(i.toArray), Option(fileType)))

                          val photo = file.mapConcat(chunk => chunk.grouped(1024))
                            .mapMaterializedValue(_ => NotUsed)
                            .via(otherChunk)
                            .concat(Source.single("1".getBytes().toSeq).via(eof))

                          val postFuture = (serviceManager ? GetPost).mapTo[PostResult]

                          onComplete(postFuture) {

                            case Success(postResult) =>

                              val postService = postResult.client

                              onComplete(call(postService, {
                                postService.client.putPicture(photo)
                              })) {

                                case Success(result) =>

                                  val reply = call(postService, {
                                    postService.client.putPost(PostPutInfo(username, result.link.get, text))
                                  })

                                  decLoad(_, postService)

                                  response(Left(reply))

                                case Failure(e) =>
                                  decLoad(_, postService)
                                  response(Right(e))
                              }

                            case Failure(e) => response(Right(e))

                          }
                      }
                  }

                case Failure(e) => response(Right(e))

              }

            case Failure(e) => response(Right(e))

          }

      }
    }
  }

}
