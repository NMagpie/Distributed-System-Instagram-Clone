package rpcImpl

import main.Main.system.dispatcher
import main.Main.{authServices, cacheService, getCurrentTime, postServices}
import services.Services.{AuthService, CacheService, PostService}
import services.{Empty, ServiceInfo, Status}
import services.gateway.GatewayService

import scala.concurrent.Future

class RpcImpl extends GatewayService {
  override def getStatus(in: Empty): Future[Status] = {

    println(s"[$getCurrentTime]: {getStatus}")

    val status = s"Server Type: Gateway\nHostname: ${main.Main.hostname}\nPort: ${main.Main.grpcPort}"
    Future(Status(status))
  }

  override def newService(in: ServiceInfo): Future[Empty] = {

    println(s"[$getCurrentTime]: {newService}")

    in.`type` match {
      case "cache" =>
        cacheService = CacheService(in.`type`, in.hostname, in.port)

      case "auth" =>
        authServices = authServices :+ AuthService(in.`type`, in.hostname, in.port)

      case "post" =>
        postServices = postServices :+ PostService(in.`type`, in.hostname, in.port)

    }

    Future(Empty())
  }
}
