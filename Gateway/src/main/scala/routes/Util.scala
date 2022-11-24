package routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.grpc.StatusRuntimeException
import logging.LogHelper.logError
import main.Main.system.dispatcher
import main.Main.{authServices, cacheServices, postServices, serviceManager}
import org.json4s.jackson.Serialization
import org.json4s.{FieldSerializer, Formats, NoTypeHints}
import scalapb.GeneratedMessage
import scalapb.json4s.JsonFormat
import services.ServiceManager._
import services.Services.{AuthService, CacheService, PostService}
import services.post.PostInfo

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

object Util {

  case class Profile(username: String, name: String, profilePicture: String, posts: Map[String, Array[PostInfo]])

  implicit val system: ActorSystem = ActorSystem("my-system")

  implicit val formats: Formats = Serialization.formats(NoTypeHints) + FieldSerializer[GeneratedMessage]()

  private val random = new Random

  def randomCache() : CacheService = cacheServices(random.nextInt(cacheServices.length))

  def response[T <: scalapb.GeneratedMessage](reply: Either[Future[T], Throwable]): Route = {
    reply match {
      case Left(replyF) =>
        onComplete(replyF) {
          case Success(replyResult) =>
            println("Reply sent")

            val json = JsonFormat.toJsonString(replyResult)

            complete(HttpEntity(ContentTypes.`application/json`, json))

          case Failure(e) =>
            if (e.getMessage == "UNKNOWN: 429 Too many requests")
              complete(HttpResponse(429, entity = "Too many requests"))
            else {
              logError(e)
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
            }
        }

      case Right(e) =>
        logError(e)
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.getMessage))
    }
  }

  def call[T, A](service: A, code: => Future[T]): Future[T] = {
    try {
      if (service == null)
        return Future.failed(new Exception("No such service available, please try again later"))

      val outFuture = for {
        c <- code
      } yield c

      outFuture.onComplete {
        case Success(_) => serviceManager ! OK(service)
        case Failure(_) => serviceManager ! Fail(service)
      }

      outFuture

    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  def filterAuth(hostname: String, port: Int): Array[AuthService] = {
    authServices.filter(aService => {
      aService.hostname == hostname && aService.port == port
    })
  }

  def filterPost(hostname: String, port: Int): Array[PostService] = {
    postServices.filter(aService => {
      aService.hostname == hostname && aService.port == port
    })
  }

  def filterCache(hostname: String, port: Int): Array[CacheService] = {
    cacheServices.filter(aService => {
      aService.hostname == hostname && aService.port == port
    })
  }

  def checkFileType(fileType: String): Boolean = {
    fileType match {
      case ".png" => true
      case ".jpg" => true
      case ".jpeg" => true
      case _ => false
    }
  }

  def decLoad(authService: AuthService = null, postService: PostService = null): Unit = {
    if (authService != null)
      serviceManager ! DecLoad(Left(authService))
    if (postService != null)
      serviceManager ! DecLoad(Right(postService))
  }

}
