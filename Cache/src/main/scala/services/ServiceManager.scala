package services

import akka.actor.Actor
import services.ServiceManager.{AddService, RemoveService, cacheServices}
import services.Services.CacheService

object ServiceManager {
  case class AddService(hostname: String, port: Int)

  case class RemoveService(hostname: String, port: Int)

  var cacheServices: Array[CacheService] = Array.empty[CacheService]
}

class ServiceManager extends Actor {
  override def receive: Receive = {
    case AddService(hostname, port) =>
      val service = CacheService(hostname, port)
      cacheServices = cacheServices :+ service

    case RemoveService(hostname, port) =>
      cacheServices = cacheServices.filter(service => {
        service.hostname != hostname && service.port != port
      })
  }
}
