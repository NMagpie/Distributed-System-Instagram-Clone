package rpcImpl

import cache.{PutResult, Quarry, QuarryResult}

import scala.concurrent.Future

class RpcImpl extends cache.CacheService {

  override def get(in: Quarry): Future[QuarryResult] = ???

  override def put(in: cache.Object): Future[PutResult] = ???

}
