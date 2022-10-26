package rpcImpl

import main.Main.system.dispatcher
import main.Main.{authServices, cacheService, getCurrentTime, postServices, serviceManager}
import services.ServiceManager.AddService
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

    serviceManager ! AddService(in.`type`, in.hostname, in.port)

    Future(Empty())
  }
}
