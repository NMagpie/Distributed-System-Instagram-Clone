package main

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Source}
import akka.util.{ByteString, Timeout}
import com.google.protobuf.{ByteString => pByteString}
import com.typesafe.config.ConfigFactory
import main.Main.system.dispatcher
import org.json4s.jackson.Serialization
import org.json4s.{FieldSerializer, Formats, NoTypeHints}
import rpcImpl.RpcImpl
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat
import services.ServiceManager._
import services.Services.{AuthService, CacheService, PostService}
import services.authentication._
import services.cache._
import services.discovery._
import services.gateway._
import services.post._
import services.{Empty, ServiceInfo, ServiceManager}

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.{Failure, Success}

/*

  GATEWAY API

  MADE BY: SOROCHIN NICHITA, FAF-191

 */

object Main {

  case class Profile(username: String, name: String, profilePicture: String, posts: Map[String, Array[PostInfo]])

  case class ProfileInfo(username: String, name: String, profilePicture: String)

  implicit val system: ActorSystem = ActorSystem("my-system")

  implicit val formats: Formats = Serialization.formats(NoTypeHints) + FieldSerializer[GeneratedMessage]()

  val hostname: String = ConfigFactory.load.getString("hostname")

  val httpPort: Int = ConfigFactory.load.getInt("httpPort")

  val grpcPort: Int = ConfigFactory.load.getInt("grpcPort")

  val discoveryHost: String = ConfigFactory.load.getString("discoveryHost")

  val discoveryPort: Int = ConfigFactory.load.getInt("discoveryPort")

  val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(discoveryHost, discoveryPort).withTls(false)

  val discovery: DiscoveryService = DiscoveryServiceClient(clientSettings)

  var Array(authServices : Array[AuthService], postServices: Array[PostService], cacheService: CacheService) = {

  val serviceMap = Await.result(discovery.discover(ServiceInfo("gateway", hostname, grpcPort)), Duration.create(15, "min"))

  val authServices = serviceMap.auth.map(service => AuthService(service.`type`, service.hostname, service.port)).toArray

  val postServices = serviceMap.post.map(service => PostService(service.`type`, service.hostname, service.port)).toArray

  val cacheService = if (serviceMap.cache.nonEmpty) {
    serviceMap.cache.map(service => CacheService(service.`type`, service.hostname, service.port)).head
  } else null

  Array(authServices, postServices, cacheService)
  }

  val serviceManager: ActorRef = system.actorOf(Props(new ServiceManager()), "serviceManager")

  implicit val timeout: Timeout = Timeout(10 seconds)

  def main(args: Array[String]): Unit = {

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

          val authFuture = (serviceManager ? GetAuth).mapTo[AuthResult]

          val username = new String(Base64.getDecoder.decode(authData), StandardCharsets.UTF_8).split(':')(0)

          println(s"[$getCurrentTime]: {register}\t$username")
          onComplete(authFuture) {

            case Success(authResult) =>

              val authService = authResult.client

              val reply = authService.client.register(UserData(authData))

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

                                  val reply = postService.client.putProfile(ProfilePutInfo(username, name))
                                  response(reply)

                                }

                                val otherChunk = Flow[ByteString].map(i => PictureInfo(pByteString.copyFrom(i.toArray), None))

                                val eof = Flow[Seq[Byte]].map(i => PictureInfo(pByteString.copyFrom(i.toArray), Option(fileType)))

                                val photo = file.mapConcat(chunk => chunk.grouped(1024))
                                  .mapMaterializedValue(_ => NotUsed)
                                  .via(otherChunk)
                                  .concat(Source.single("1".getBytes().toSeq).via(eof))

                                onComplete(postService.client.putPicture(photo)) {

                                  case Success(result) =>
                                    val reply = postService.client.putProfile(ProfilePutInfo(username, name, result.link.get))
                                    serviceManager ! DecLoad(Left(authService))
                                    serviceManager ! DecLoad(Right(postService))
                                    response(reply)

                                  case Failure(e) => e.printStackTrace()
                                    serviceManager ! DecLoad(Left(authService))
                                    serviceManager ! DecLoad(Right(postService))
                                    complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                                }

                              case Failure(e) => e.printStackTrace()
                                serviceManager ! DecLoad(Left(authService))
                                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                            }
                        }
                    }
                  } else {
                    serviceManager ! DecLoad(Left(authService))
                    complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, replyResult.toString))
                  }

                case Failure(e) => e.printStackTrace()
                  serviceManager ! DecLoad(Left(authService))
                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
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

        val authFuture = (serviceManager ? GetAuth).mapTo[AuthResult]

        onComplete(authFuture) {

          case Success(authResult) =>

            val authService = authResult.client

            println(s"[$getCurrentTime]: {login}\t$authData")

            val reply = authService.client.auth(UserData(authData))

            serviceManager ! DecLoad(Left(authService))

            response(reply)

          case Failure(e) => e.printStackTrace()
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
        }

    }

  }

  val getPosts: Route = pathPrefix("profile" / Segment / IntNumber) {
    case (username, dozen) =>

      val query = cacheService.client.query(Query("get",
        s"{\"what\": \"getPost\"," +
        s" \"of\": \"$username\"," +
        s"\"dozen\": \"$dozen\"}"))

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

                println(s"[$getCurrentTime]: {getPosts}\t$username\t$dozen")

                val postService = postResult.client

                val reply = postService.client.getPost(PostParams(username, dozen))

                serviceManager ! DecLoad(Right(postService))

                reply.onComplete {
                  case Success(replyResult) =>

                    val postInfo = Profile(username, null, null, Map(s"$dozen" -> replyResult.postInfo.toArray))

                    cacheService.client.query(Query("put", Serialization.write(postInfo)))
                }

                response(reply)

              case Failure(e) => e.printStackTrace()
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
            }
          }

        case Failure(e) => e.printStackTrace()
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
      }

  }

  val getProfile: Route = path("profile" / Segment) {
    username =>

      val query = cacheService.client.query(Query("get",
        s"{\"what\": \"getProfile\"," +
          s" \"of\": \"$username\"}"))

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

                println(s"[$getCurrentTime]: {getProfile}\t$username")

                val reply = postService.client.getProfile(Username(username))

                serviceManager ! DecLoad(Right(postService))

                reply.onComplete {
                  case Success(replyResult) =>

                    val postInfo = Profile(username, replyResult.name, replyResult.profilePicture, null)

                    cacheService.client.query(Query("put", Serialization.write(postInfo)))
                }

                response(reply)

              case Failure(e) => e.printStackTrace()
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
            }

          }

        case Failure(e) => e.printStackTrace()
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
      }

  }

  val putPost: Route = post {
    path("upload") {
      headerValueByName("Key") {
        key =>
          val authFuture = (serviceManager ? GetAuth).mapTo[AuthResult]

          onComplete(authFuture) {
            case Success(authResult) =>

              val authService = authResult.client

              val reply = authService.client.whoIsThis(AuthKey(key))

              onComplete(reply) {
                case Success(usernameM) =>

                  val username = usernameM.username

                  formFields("text") {
                    text =>

                      println(s"[$getCurrentTime]: {putPost}\t$username\t$text")

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

                              onComplete(postService.client.putPicture(photo)) {

                                case Success(result) =>

                                  val reply = postService.client.putPost(PostPutInfo(username, result.link.get, text))

                                  serviceManager ! DecLoad(Right(postService))

                                  response(reply)

                                case Failure(e) => e.printStackTrace()

                                  serviceManager ! DecLoad(Right(postService))

                                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
                              }

                            case Failure(e) => e.printStackTrace()
                              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))

                          }
                      }
                  }

                case Failure(e) => e.printStackTrace()
                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))

              }

            case Failure(e) => e.printStackTrace()
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))

          }

        ////////////////////////////////////////
      }
    }
  }

  val getStatus: Route = path("status") {
    formFields("service") {
      service =>
        val Array(sType, sHostname, sPort) = service.split(":")

        val intPort = sPort.toInt

        println(s"[$getCurrentTime]: {getStatus}\t$service")

        sType match {
          case "gateway" =>

            val status =
              if (sHostname == hostname && intPort == grpcPort)
                s"{\n\"message\": \"Server Type: Gateway\\nHostname: $hostname\\nPort: $grpcPort\""
              else
                "Such service does not exist"
            complete(HttpEntity(ContentTypes.`application/json`, status))

          case "cache" =>

            if (sHostname == cacheService.hostname && intPort == cacheService.port)
              response(cacheService.client.getStatus(Empty()))
            else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

          case "auth" =>

            val authServiceFiltered = authServices.filter(aService => {
              aService.hostname == sHostname && aService.port == intPort
            })

            if (authServiceFiltered.nonEmpty)
              response(authServiceFiltered(0).client.getStatus(Empty()))
            else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

          case "post" =>

            val postServiceFiltered = postServices.filter(pService => {
              pService.hostname == sHostname && pService.port == intPort
            })

            if (postServiceFiltered.nonEmpty)
              response(postServiceFiltered(0).client.getStatus(Empty()))
            else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

          case "discovery" =>

            if (sHostname == discoveryHost && intPort == discoveryPort)
              response(discovery.getStatus(Empty()))
            else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

        }

    }

  }

  def response(reply: Future[GeneratedMessage]): Route = {
    onComplete(reply) {
      case Success(replyResult) =>
        println("Reply sent")

        val json = JsonFormat.toJsonString(replyResult)

        complete(HttpEntity(ContentTypes.`application/json`, json))

      case Failure(e) =>
        if (e.getMessage == "UNKNOWN: 429 Too many requests")
          complete(HttpResponse(429, entity = "Too many requests"))
        else
          e.printStackTrace()
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