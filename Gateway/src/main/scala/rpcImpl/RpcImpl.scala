package rpcImpl

import gateway._
import main.Main.{executionContext, getCurrentTime}

import scala.concurrent.Future

class RpcImpl extends GatewayService {
  override def getStatus(in: Empty): Future[Status] = {

    println(s"[$getCurrentTime]: {getStatus}")

    val status = s"Server Type: Gateway\nHostname: ${main.Main.hostname}\nPort: ${main.Main.grpcPort}"
    Future(Status(status))
  }
}
