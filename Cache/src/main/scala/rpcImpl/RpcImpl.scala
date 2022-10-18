package rpcImpl

import cache._
import taskLimiter.tlActor._
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import discovery.Message
import io.grpc.{Status => grpcStatus}
import main.Main.{client, getCurrentTime, taskLimiter}
import main.Main.system.dispatcher
import org.json4s.native.JsonMethods
import org.json4s.native.JsonMethods.parse
import org.json4s.{Formats, NoTypeHints, jackson}

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import org.json4s.native.Serialization.{read, write}

object RpcImpl {

  case class Get(what: String, of: String, dozen: Option[String])

  case class Post(photo: String, text: String)

  case class Profile(username: String, name: String, profilePicture: String, posts: MMap[String, Array[Post] ] )

  case class ProfileInfo(username: String, name: String, profilePicture: String)
}

class RpcImpl extends cache.CacheService {

  import akka.pattern.ask

  implicit def json4sJacksonFormats: Formats = jackson.Serialization.formats(NoTypeHints)

  import RpcImpl._

  implicit val timeout: Timeout = Timeout(10 seconds)

  val profiles : MMap[String, Profile] = MMap[String, Profile]()

  override def query(in: Query): Future[QueryResult] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    println(s"[$getCurrentTime]: [${result.load} of ${result.limit}] {auth}\t$in")
    //println(s"[$getCurrentTime]: {query}\t$in")

    if (result.result) {

      try {

        in.method match {
          case "get" => {
            val query = read[Get](in.message)

            val profile = profiles.getOrElse(query.of, null)

            if (profile == null) {
              taskLimiter ! Free
              Future.successful(QueryResult("null"))
            } else {
              query.what match {
                case "getProfile" =>
                  val profileInfo = if (profile.name == null) "null" else write( ProfileInfo(profile.username, profile.name, profile.profilePicture) )

                  taskLimiter ! Free
                  Future.successful( QueryResult( profileInfo ) )

                case "getPost" =>

                  val dozenPosts = profile.posts(query.dozen.get)

                  val posts = if (dozenPosts.isEmpty) "null" else write( dozenPosts )

                  taskLimiter ! Free
                  Future.successful( QueryResult( posts ) )

                case _ =>
                  taskLimiter ! Free
                  Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("Yo mama")))
              }
            }

          }

          case "put" => {

            val query = read[Profile](in.message)

            val profile = profiles.getOrElse(query.username, null)

            if (profile == null) {
              println(query)
              profiles.addOne(query.username, query)
            } else {
              val username = if (query.username != null) query.username else profile.username
              val name = if (query.name != null) query.name else profile.name
              val profilePicture = if (query.profilePicture != null) query.profilePicture else profile.profilePicture

              val mergedMap =
                if (query.posts.isEmpty)
                  query.posts ++ profile.posts.map { case (k,v) => k -> query.posts.getOrElse(k,v) }
                else
                  profile.posts

              val updatedProfile = Profile(username, name, profilePicture, mergedMap)

              //println(updatedProfile)

              profiles.addOne(username, updatedProfile)
            }

            taskLimiter ! Free
            Future.successful(QueryResult("Good"))
          }

          case _ =>
            taskLimiter ! Free
            Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("Yo mama")))
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
