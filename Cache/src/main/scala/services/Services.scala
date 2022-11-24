package services

import akka.grpc.GrpcClientSettings
import main.Main.system
import services.cache.CacheServiceClient

object Services {

  case class CacheService(
                           hostname: String,
                           port: Int,
                           client: CacheServiceClient,
                         )

  object CacheService {

    def apply(
              hostname: String,
              port: Int): CacheService =
      CacheService(hostname, port,
        CacheServiceClient(GrpcClientSettings.connectToServiceAt(hostname, port).withTls(false)))
  }

}
