package cacheManager

import akka.actor.Actor
import cacheManager.CacheManager.{Posts, Remove, posts, profiles}
import main.Main.system
import main.Main.system.dispatcher
import rpcImpl.RpcImpl.{Post, Profile}

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

object CacheManager {
  case class Remove(username: String)

  case class Posts(posts: MMap[String, (Array[Post], Long)])

  val profiles: MMap[String, Profile] = MMap[String, Profile]()

  var posts: MMap[String, (Array[Post], Long)] = MMap[String, (Array[Post], Long)]()
}

class CacheManager(maxAge: FiniteDuration) extends Actor {

  override def receive: Receive = {
    case username : String =>
      val profile = profiles.getOrElse(username, null)
      sender() ! profile

    case p @ Profile(username, _, _, _) =>
      profiles.put(username , p)
      system.scheduler.scheduleOnce(maxAge, self, Remove(username))

    case Posts(newPosts) =>
      posts = newPosts ++ posts.map { case (k, v) => k -> newPosts.getOrElse(k, v) }

    case Remove(username) =>
      profiles.remove(username)
  }

}
