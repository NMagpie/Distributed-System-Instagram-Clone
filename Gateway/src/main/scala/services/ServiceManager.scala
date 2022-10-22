package services

import akka.actor.Actor
import services.Services.{AuthService, PostService}
import services.ServiceManager.{AuthResult, DecLoad, GetAuth, GetPost, PostResult}
import main.Main.{authServices, postServices}

object ServiceManager {
  case class GetAuth()

  case class AuthResult(client: AuthService)

  case class GetPost()

  case class PostResult(client: PostService)

  case class DecLoad(client: Either[AuthService, PostService])
}

class ServiceManager extends Actor {

  override def receive: Receive = {

    case GetAuth =>
      val client = authServices.minBy(_.load)
      client.load = client.load + 1
      sender() ! AuthResult(client)

    case GetPost =>
      val client = postServices.minBy(_.load)
      client.load = client.load + 1
      sender() ! PostResult(client)

    case DecLoad(service) =>
      service match {
        case Right(postService) =>
          postService.load = postService.load - 1

        case Left(authService) =>
          authService.load = authService.load - 1
      }

  }

}
