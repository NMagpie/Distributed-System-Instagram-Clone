package caching

import akka.actor.Actor
import caching.CacheActor.{Put, Remove}
import main.Main.cache
import services.authentication.RegisterData

object CacheActor {
  case class Put(userData: RegisterData)

  case class Remove(id: Int)
}

class CacheActor extends Actor {

  override def receive: Receive = {
    case Put(userData) =>
      cache = cache + (userData.id -> userData)
    case Remove(id) =>
      cache = cache - id
  }
}
