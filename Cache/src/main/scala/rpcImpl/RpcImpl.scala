package rpcImpl

import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.{Status => grpcStatus}
import main.Main.system.dispatcher
import services.cache._
import services.{Empty, Status}
import taskLimiter.TlActor._
import main.Main.{cacheMng, getCurrentTime, taskLimiter}
import cacheManager.CacheManager
import org.json4s.native.Serialization.{read, write}
import org.json4s.{Formats, NoTypeHints, jackson}

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Success

object RpcImpl {

  case class Get(what: String, of: String, dozen: Option[String])

  case class Post(photo: String, text: String)

  case class Profile(username: String, name: String, profilePicture: String, posts: MMap[String, Array[Post]])

  case class ProfileInfo(username: String, name: String, profilePicture: String)
}

class RpcImpl extends CacheService {

  import akka.pattern.ask

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  import RpcImpl._

  implicit val timeout: Timeout = Timeout(10 seconds)

  val profiles: MMap[String, Profile] = MMap[String, Profile]()

  override def query(in: Query): Future[QueryResult] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    println(s"[$getCurrentTime]: [${result.load} of ${result.limit}] {query}\t$in")

    if (result.result) {

      try {

        in.method match {
          case "get" => {
            val query = read[Get](in.message)

              val profile = CacheManager.profiles.getOrElse(query.of, null)

              if (profile == null) {
                taskLimiter ! Free
                Future.successful(QueryResult("null"))
              } else {
                query.what match {
                  case "getProfile" =>
                    val profileInfo = if (profile.name == null) "null" else write(ProfileInfo(profile.username, profile.name, profile.profilePicture))

                    taskLimiter ! Free
                    Future.successful(QueryResult(profileInfo))

                  case "getPost" =>

                    val dozenPosts = profile.posts.getOrElse(query.dozen.get, Array.empty)

                    val posts = if (dozenPosts.isEmpty) "null" else write(dozenPosts)

                    taskLimiter ! Free
                    Future.successful(QueryResult(posts))

                  case _ =>
                    taskLimiter ! Free
                    Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("Unknown method")))
                }
              }

          }

          case "put" => {

            val oldQuery = read[Profile](in.message)

            val query = oldQuery.copy(posts = MMap[String, Array[Post]]())

            val profile = CacheManager.profiles.getOrElse(query.username, null)

            if (profile == null) {
              cacheMng ! query
              //profiles.put(query.username, query)
            } else {
              val username = if (query.username != null) query.username else profile.username
              val name = if (query.name != null) query.name else profile.name
              val profilePicture = if (query.profilePicture != null) query.profilePicture else profile.profilePicture

              val mergedMap =
                if (query.posts.nonEmpty)
                  query.posts ++ profile.posts.map { case (k, v) => k -> query.posts.getOrElse(k, v) }
                else
                  profile.posts

              val updatedProfile = Profile(username, name, profilePicture, mergedMap)

              cacheMng ! updatedProfile
              //profiles.put(username, updatedProfile)
            }

            taskLimiter ! Free
            Future.successful(QueryResult("Good"))
          }

          case _ =>
            taskLimiter ! Free
            Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("Unknown method")))
        }

      } catch {
        case e: Exception => println(e.getMessage)
          taskLimiter ! Free
          Future.failed(e)
      }

    } else {
      taskLimiter ! Free
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))
    }
  }

  override def getStatus(in: Empty): Future[Status] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    if (result.result) try {

      println(s"[$getCurrentTime]: {getStatus}")

      val status = s"Server Type: Cache\nHostname: ${main.Main.hostname}\nPort: ${main.Main.port}"

      taskLimiter ! Free
      Future(Status(status))

    } catch {
      case e: Exception =>
        taskLimiter ! Free
        Future.failed(e)
    } else
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))
  }

}
