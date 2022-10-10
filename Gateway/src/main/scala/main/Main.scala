package main

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import discovery._
import gateway._
import rpcImpl.RpcImpl
import scalapb.GeneratedMessage
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import com.google.protobuf.{ByteString => pByteString}
import scalapb.descriptors.{Descriptor, FieldDescriptor}

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.{Failure, Success}

/*

  GATEWAY API

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "my-system")

  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  val hostname: String = ConfigFactory.load.getString("hostname")

  val httpPort: Int = ConfigFactory.load.getInt("httpPort")

  val grpcPort: Int = ConfigFactory.load.getInt("grpcPort")

  val discoveryHost: String = ConfigFactory.load.getString("discoveryHost")

  val discoveryPort: Int = ConfigFactory.load.getInt("discoveryPort")

  val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(discoveryHost, discoveryPort).withTls(false)

  val client: DiscoveryService = DiscoveryServiceClient(clientSettings)

  def main(args: Array[String]): Unit = {

    Await.ready(client.discover(ServiceInfo("gateway", hostname, grpcPort)), Duration.create(15, "min"))

    val bindServer = Http().newServerAt(hostname, grpcPort).bind(GatewayServiceHandler(new RpcImpl))

    val route = {
      register ~ login ~ getPosts ~ getProfile ~ putPost ~ getStatus
    }

    val bindingFuture = Http().newServerAt(hostname, httpPort).bind(route)

    println(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

    bindServer
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

  }

  val register: Route = post {

    path("register") {
      headerValueByName("Authorization") {
        authData =>
          val request = s"{\"encode\": \"$authData\"}"
          val reply = client.sendMessage(Message("register", request))

          val username = new String(Base64.getDecoder.decode(authData), StandardCharsets.UTF_8).split(':')(0)

          println(s"[$getCurrentTime]: {register}\t$username")

          onComplete(reply) {
            case Success(replyResult) =>
              if (replyResult.success) {

                formFields("name") {
                  name =>
                    fileUpload("photo") {
                      case (metaData, file) =>

                        val fileType = "." + metaData.getContentType.mediaType.subType

                        if (!checkFileType(fileType)) {
                          val request = s"{\"username\": \"$username\", \"name\": \"$name\", " +
                            s"\"avatar\": \"\"}"
                          val reply = client.sendMessage(Message("putProfile", request))
                          response(reply)
                        }

                        val otherChunk = Flow[ByteString].map(i => PictureInfo(pByteString.copyFrom(i.toArray), None))

                        val eof = Flow[Seq[Byte]].map(i => PictureInfo(pByteString.copyFrom(i.toArray), Option(fileType)))

                        val photo = file.mapConcat(chunk => chunk.grouped(1024))
                          .mapMaterializedValue(_ => NotUsed)
                          .via(otherChunk)
                          .concat(Source.single("1".getBytes().toSeq).via(eof))

                        onComplete(client.putPicture(photo)) {

                          case Success(result) =>
                            val request = s"{\"username\": \"$username\", \"name\": \"$name\", " +
                              s"\"avatar\": \"${result.link.get}\"}"
                            val reply = client.sendMessage(Message("putProfile", request))
                            response(reply)

                          case Failure(e) => e.printStackTrace()
                            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                        }
                    }
                }
              } else {
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, replyResult.toString))
              }

            case Failure(e) => e.printStackTrace()
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
          }
      }
    }
  }

  val login: Route = path("login") {
    headerValueByName("Authorization") {
      authData =>

        println(s"[$getCurrentTime]: {login}\t$authData")

        val request = s"{\"encode\": \"$authData\"}"
        val reply = client.sendMessage(Message("auth", request))
        response(reply)
    }
  }

  val getPosts: Route = pathPrefix("profile" / Segment / IntNumber) {
    case (username, dozen) =>

      println(s"[$getCurrentTime]: {getPosts}\t$username\t$dozen")

      val request = s"{\"username\": \"$username\", \"dozen\": \"$dozen\"}"
      val reply = client.sendMessage(Message("getPost", request))
      response(reply)
  }

  val getProfile: Route = path("profile" / Segment) {
    username =>

      println(s"[$getCurrentTime]: {getProfile}\t$username")

      val request = s"{\"username\": \"$username\"}"
      val reply = client.sendMessage(Message("getProfile", request))
      response(reply)
  }

  val putPost: Route = post {
    path("upload") {
      headerValueByName("Key") {
        key =>
          formFields("text") {
            text =>

              println(s"[$getCurrentTime]: {putPost}\t$key\t$text")

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

                  onComplete(client.putPicture(photo)) {

                    case Success(result) =>
                      val request = s"{\"key\": \"$key\", \"photo\": \"${result.link.get}\", " +
                        s"\"text\": \"$text\"}"
                      val reply = client.sendMessage(Message("putPost", request))
                      response(reply)

                    case Failure(e) => e.printStackTrace()
                      complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                  }
              }
          }
      }
    }
  }

  val getStatus: Route = path("status") {
      formFields("service") {
        service =>
          println(s"[$getCurrentTime]: {getStatus}\t$service")

          val reply = client.sendMessage(Message("getStatus", service))

          response(reply)
      }
    }

  def response(reply: Future[GeneratedMessage]): Route = {
    onComplete(reply) {
      case Success(replyResult) => println("Reply sent")

        if (replyResult.getFieldByNumber(2).toString.equals("Error: 2 UNKNOWN: 429 Too many requests"))
          complete(HttpResponse(429, entity = "Too many requests"))
        else
          complete(HttpEntity(ContentTypes.`application/json`,
            replyResult.toString
          ))
      case Failure(e) => e.printStackTrace()
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
    }
  }

  def checkFileType(fileType: String): Boolean = {
    fileType match {
      case ".png" => true
      case ".jpg" => true
      case ".jpeg" => true
      case _ => false
    }
  }

  def getCurrentTime: String = {
    val timestamp = LocalDateTime.now()
    DateTimeFormatter.ofPattern("HH:mm:ss").format(timestamp)
  }

}