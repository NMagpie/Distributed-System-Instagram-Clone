package services

import akka.actor.{Actor, Cancellable}
import logging.LogHelper.logMessage
import main.Main
import main.Main.system.dispatcher
import main.Main.{authServices, cacheServices, postServices, system}
import services.ServiceManager._
import services.Services.{AuthService, CacheService, PostService}

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

object ServiceManager {
  case class GetAuth(prepare: Boolean = false)

  case class AuthResult(client: AuthService, id: Int = -1)

  case class GetPost(prepare: Boolean = false)

  case class PostResult(client: PostService, id: Int = -1)

  case class DecLoad(client: Either[AuthService, PostService])

  case class Fail(service: Any)

  case class OK(service: Any)

  case class AddService(`type`: String, hostname: String, port: Int)
}

class ServiceManager extends Actor {

  val timers: MMap[String, Cancellable] = MMap[String, Cancellable]()

  val maxTime: FiniteDuration = 1 minute

  val errorLimit: Int = 10

  var prepareId: Int = 0

  override def receive: Receive = {

    case GetAuth =>
      if (authServices.nonEmpty) {
        val client = authServices.minBy(_.load)
        client.load = client.load + 1
        sender() ! AuthResult(client)
      } else
        sender() ! AuthResult(null)

    case GetAuth(prepare) =>
      if (authServices.nonEmpty) {
        val id = getId(prepare)
        val client = authServices.minBy(_.load)
        client.load = client.load + 1
        sender() ! AuthResult(client, id)
      } else
        sender() ! AuthResult(null)

    case GetPost(prepare) =>
      if (postServices.nonEmpty) {
        val id = getId(prepare)
        val client = postServices.minBy(_.load)
        client.load = client.load + 1
        sender() ! PostResult(client, id)
      } else
        sender() ! AuthResult(null)

    case DecLoad(service) =>
      service match {
        case Right(postService) =>
          postService.load = postService.load - 1

        case Left(authService) =>
          authService.load = authService.load - 1
      }

    case Fail(service) => {
          service match {
            case service: AuthService =>
              val timer = timers.getOrElseUpdate("auth" + service.hostname + service.port, system.scheduler.scheduleOnce(maxTime) {
                //println("Time has passed!")
                service.errors = 0
                timers.remove("auth" + service.hostname + service.port)
              })

              service.errors = service.errors + 1

              if (service.errors >= errorLimit) {
                authServices = authServices.filter(aService => {
                  aService.hostname != service.hostname && aService.port != service.port
                })

                timer.cancel()

                timers.remove("auth" + service.hostname + service.port)

                Main.discovery.removeService(ServiceInfo("auth", service.hostname, service.port))

                logMessage(s"{removed}\tauth:${service.hostname}:${service.port}: errors[${service.errors} of $errorLimit]")
              } else {
                logMessage(s"{errors}\tauth:${service.hostname}:${service.port}: errors[${service.errors} of $errorLimit]")
              }


            case service: PostService =>
              val timer = timers.getOrElseUpdate("post" + service.hostname + service.port, system.scheduler.scheduleOnce(maxTime) {
                service.errors = 0
              })

              service.errors = service.errors + 1

              if (service.errors >= errorLimit) {
                postServices = postServices.filter(pService => {
                  pService.hostname != service.hostname && pService.port != service.port
                })

                timer.cancel()

                timers.remove("post" + service.hostname + service.port)

                Main.discovery.removeService(ServiceInfo("post", service.hostname, service.port))
              }

            case service: CacheService =>
              val timer = timers.getOrElseUpdate("cache" + service.hostname + service.port, system.scheduler.scheduleOnce(maxTime) {
                service.errors = 0
              })

              service.errors = service.errors + 1

              if (service.errors >= errorLimit) {
                cacheServices = cacheServices.filter(aSerivce=> aSerivce.equals(service))

                timer.cancel()

                timers.remove("cache" + service.hostname + service.port)

                Main.discovery.removeService(ServiceInfo("cache", service.hostname, service.port))
              }
          }

    }

    case OK(service) => {
      service match {

        case service: AuthService =>
          val timer = timers.getOrElseUpdate("auth" + service.hostname + service.port, system.scheduler.scheduleOnce(maxTime) {
            service.errors = 0
          })

          service.errors = 0

          timer.cancel()

          timers.remove("auth" + service.hostname + service.port)

        case service: PostService =>
          val timer = timers.getOrElseUpdate("post" + service.hostname + service.port, system.scheduler.scheduleOnce(maxTime) {
            service.errors = 0
          })

          service.errors = 0

          timer.cancel()

          timers.remove("post" + service.hostname + service.port)

        case service: CacheService =>
          val timer = timers.getOrElseUpdate("cache" + service.hostname + service.port, system.scheduler.scheduleOnce(maxTime) {
            service.errors = 0
          })

          service.errors = 0

          timer.cancel()

          timers.remove("cache" + service.hostname + service.port)
      }
    }

    case AddService(sType, hostname, port) =>
      sType match {
        case "cache" =>
          cacheServices = cacheServices :+ CacheService(sType, hostname, port)

        case "auth" =>
          authServices = authServices :+ AuthService(sType, hostname, port)

        case "post" =>
          postServices = postServices :+ PostService(sType, hostname, port)

      }

  }

  def getId(prepare: Boolean): Int = {
    if (prepare) {
      prepareId += 1
      prepareId
    } else -1
  }

}
