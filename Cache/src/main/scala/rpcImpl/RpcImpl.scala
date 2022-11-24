package rpcImpl

import akka.grpc.GrpcServiceException
import akka.util.Timeout
import cacheManager.CacheManager
import cacheManager.CacheManager.Posts
import io.grpc.{Status => grpcStatus}
import main.Main.system.dispatcher
import services.cache._
import services.{Empty, ServiceInfo, Status}
import taskLimiter.TlActor._
import main.Main.{cacheMng, getCurrentTime, hostname, port, serviceMng, taskLimiter}
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.{read, write}
import org.json4s.{Formats, NoTypeHints, jackson}
import services.ServiceManager.{AddService, RemoveService, cacheServices}

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

object RpcImpl {

  case class Get(what: String, of: String, dozen: Option[String])

  case class Post(photo: String, text: String)

  case class Profile(username: String, name: String, profilePicture: String, timestamp: Long)

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

                query.what match {
                  case "getProfile" =>
                    val profile = CacheManager.profiles.getOrElse(query.of, null)

                    if (profile == null) {
                      taskLimiter ! Free
                      return Future.successful(QueryResult("null"))
                    }

                    val profileInfo = if (profile.name == null) "null" else write(ProfileInfo(profile.username, profile.name, profile.profilePicture))

                    taskLimiter ! Free
                    Future.successful(QueryResult(profileInfo))

                  case "getPost" =>

                    val dozenPosts = CacheManager.posts.getOrElse(query.of+"-"+query.dozen.get, (Array.empty[Post], 0))._1

                    val posts = if (dozenPosts.isEmpty) "null" else write(dozenPosts)

                    taskLimiter ! Free
                    Future.successful(QueryResult(posts))

                  case _ =>
                    taskLimiter ! Free
                    Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("Unknown method")))
                }

          }

          case "put" => {

            val timestamp = System.currentTimeMillis()

            //val query = read[Profile](in.message)

            //val query = oldQuery.copy(posts = MMap[String, (Array[Post], Long)]())

            val json = parse(in.message)

            val username = (json \ "username").extract[String]

            val name = (json \ "name").extract[String]

            val profilePicture = (json \ "profilePicture").extract[String]

            val posts = (json \ "posts").extract[MMap[String, Array[Post]]].map({ case (k, v) => username+"-"+k -> (v, timestamp) })

            val profile = CacheManager.profiles.getOrElse(username, null)

            if (profile == null) {
              cacheMng ! Profile(username, name, profilePicture, timestamp)

              if (posts.nonEmpty)
                cacheMng ! Posts(posts)

              cacheServices.foreach(service => service.client.push(CMessage(in.message, timestamp)))
            } else {

              val mUsername = if (username != null) username else profile.username
              val mName = if (name != null) name else profile.name
              val mProfilePicture = if (profilePicture != null) profilePicture else profile.profilePicture

              val updatedProfile = Profile(mUsername, mName, mProfilePicture, timestamp)

              cacheMng ! updatedProfile

              if (posts.nonEmpty)
                cacheMng ! Posts(posts)

              cacheServices.foreach(service => service.client.push(CMessage(in.message, timestamp)))

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

  def newService(in: ServiceInfo): scala.concurrent.Future[services.Empty] = {
    println(s"[$getCurrentTime]: {newService}\t${in.hostname}\t${in.port}")
    if (in.port != port)
      serviceMng ! AddService(in.hostname, in.port)
    Future.successful(Empty())
  }

  def removeService(in: ServiceInfo): scala.concurrent.Future[services.Empty] = {
    println(s"[$getCurrentTime]: {removeService}\t${in.hostname}\t${in.port}")
    if (in.port != port)
      serviceMng ! RemoveService(in.hostname, in.port)
    Future.successful(Empty())
  }

  def push(in: CMessage): scala.concurrent.Future[services.Empty] = {

    val timestamp = in.time

    val json = parse(in.message)

    val username = (json \ "username").extract[String]

    val name = (json \ "name").extract[String]

    val profilePicture = (json \ "profilePicture").extract[String]

    val posts = (json \ "posts").extract[MMap[String, Array[Post]]].map({ case (k, v) => username + "-" + k -> (v, timestamp) })

    val profile = CacheManager.profiles.getOrElse(username, null)

    println(s"[$getCurrentTime]: {push} $username")

    if (profile == null) {
      cacheMng ! Profile(username, name, profilePicture, timestamp)

      if (posts.nonEmpty)
        cacheMng ! Posts(posts)
    } else {

      if (profile.timestamp > timestamp)
        return Future.successful(Empty())

      val mUsername = if (username != null) username else profile.username
      val mName = if (name != null) name else profile.name
      val mProfilePicture = if (profilePicture != null) profilePicture else profile.profilePicture

      val updatedProfile = Profile(mUsername, mName, mProfilePicture, timestamp)

      cacheMng ! updatedProfile

      //cacheServices.foreach(service => service.client.push(CMessage(in.message, timestamp)))

      if (posts.nonEmpty)
        cacheMng ! Posts(posts)

    }

    taskLimiter ! Free
    Future.successful(Empty())
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
