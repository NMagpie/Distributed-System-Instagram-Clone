package routes

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.google.protobuf.{ByteString => pByteString}
import logging.LogHelper.logMessage
import main.Main.system.dispatcher
import main.Main.{serviceManager, timeout}
import services.Id
import services.ServiceManager._
import services.authentication._
import services.post._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Register {

  import routes.Util._

  val register: Route = post {

    path("register") {
      headerValueByName("Authorization") {
        authData =>

          val bothFuture = (serviceManager ? GetBoth).mapTo[BothResult]

          val username = new String(Base64.getDecoder.decode(authData), StandardCharsets.UTF_8).split(':')(0)

          logMessage(s"{register}\t$username")

          onComplete(bothFuture) {
          case Success(result) =>

            formFields("name") {
              name =>
                fileUpload("photo") {
                  case (metaData, file) => {

                    val authService = result.aClient
                    val postService = result.pClient

                    val id = result.id

                    val delayedFuture = akka.pattern.after(10 second)({
                      decLoad(authService, postService)
                      Future.failed(new IllegalStateException("One of the services does not respond, timout process"))
                    })

                    val authReplyFuture = call(authService, {
                      authService.client.register(RegisterData(authData, id))
                    })

                    val fileType = "." + metaData.getContentType.mediaType.subType

                    val postReplyFuture = if (!checkFileType(fileType) || file == null) {

                      call(postService, {
                        postService.client.putProfile(ProfilePutInfo(username, name))
                      })
                    } else {
                      val otherChunk = Flow[ByteString].map(i => PictureInfo(pByteString.copyFrom(i.toArray), None))

                      val eof = Flow[Seq[Byte]].map(i => PictureInfo(pByteString.copyFrom(i.toArray), Option(fileType)))

                      val photo = file.mapConcat(chunk => chunk.grouped(1024))
                        .mapMaterializedValue(_ => NotUsed)
                        .via(otherChunk)
                        .concat(Source.single("1".getBytes().toSeq).via(eof))

                      val putPicture = call(postService, {
                        postService.client.putPicture(photo)
                      })

                      for {
                        pPR <- putPicture
                        pR <- {
                          call(postService, {
                            postService.client.putProfile(ProfilePutInfo(username, name, pPR.link.get, id))
                          })
                        }
                      } yield pR

                    }

                    val generalFuture = for {
                      aR <- authReplyFuture
                      pR <- postReplyFuture
                    } yield (aR, pR)

                    val firstFuture = Future firstCompletedOf Seq(generalFuture, delayedFuture)

                    onComplete(firstFuture) {
                      case Success(replies) => {
                        val authReply = replies._1
                        val postReply = replies._2

                        if (authReply.error.isDefined || !postReply.success) {
                          /////  WRONG RESPONSES => ROLLBACK CHANGES

                          call(postService, {
                            postService.client.rollback(Id(id))
                          })

                          call(authService, {
                            authService.client.rollback(Id(id))
                          })

                          decLoad(authService, postService)

                          val response = (if (authReply.error.isDefined) authReply.toString+"\n" else "") +
                            (if (!postReply.success) postReply.error.get else "")

                          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, response))
                        } else {
                          ///// EVERYTHING IS OK => COMMIT CHANGES

                          call(postService, {
                            postService.client.commit(Id(id))
                          })

                          val authConfirm = call(authService, {
                            authService.client.commit(Id(id))
                          })

                          decLoad(authService, postService)

                          response(Left(authConfirm))
                        }
                      }
                      case Failure(e) =>
                        ///// EXCEPTION DURING SERVICES CALL => ROLLBACK CHANGES
                        call(postService, {
                          postService.client.rollback(Id(id))
                        })

                        call(authService, {
                          authService.client.rollback(Id(id))
                        })

                        decLoad(authService, postService)
                        response(Right(e))
                    }

                  }
                }
            }

          case Failure(e) => response(Right(e))
        }
      }
    }
  }

}
