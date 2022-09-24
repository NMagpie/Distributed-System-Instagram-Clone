package rpcImpl

import general._

import scala.concurrent.Future

import main.Main.executionContext

class RpcGeneral extends General {

  def status(in: StatusParams): Future[Status] = Future(Status("hello"))

}
