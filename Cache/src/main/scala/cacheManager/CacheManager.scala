package cacheManager

import akka.actor.Actor
import cacheManager.CacheManager.{Remove, profiles}
import rpcImpl.RpcImpl.Profile
import main.Main.system
import main.Main.system.dispatcher

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.Map
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.language.postfixOps

object CacheManager {
  case class Remove(username: String)

  val profiles: Map[String, Profile] = new ConcurrentHashMap[String, Profile]().asScala
}

class CacheManager(maxAge: FiniteDuration) extends Actor {

  override def receive: Receive = {
    case username : String =>
      val profile = profiles.getOrElse(username, null)
      sender() ! profile

    case p @ Profile(username, _, _, _) =>
      profiles.put(username , p)
      system.scheduler.scheduleOnce(maxAge, self, Remove(username))

    case Remove(username) =>
      profiles.remove(username)
  }

}
