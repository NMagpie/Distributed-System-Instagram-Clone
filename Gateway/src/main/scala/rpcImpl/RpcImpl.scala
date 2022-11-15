package rpcImpl

import logging.LogHelper.logMessage
import main.Main.system.dispatcher
import main.Main.serviceManager
import services.ServiceManager.AddService
import services.gateway.GatewayService
import services.{Empty, ServiceInfo, Status}

import scala.concurrent.Future

class RpcImpl extends GatewayService {
  override def getStatus(in: Empty): Future[Status] = {

    logMessage("{getStatus}")

    val status = s"Server Type: Gateway\nHostname: ${main.Main.hostname}\nPort: ${main.Main.grpcPort}"
    Future(Status(status))
  }

  override def newService(in: ServiceInfo): Future[Empty] = {

    logMessage(s"{newService}\t${in.`type`}\t${in.hostname}\t${in.port}")

    serviceManager ! AddService(in.`type`, in.hostname, in.port)

    Future(Empty())
  }
}
