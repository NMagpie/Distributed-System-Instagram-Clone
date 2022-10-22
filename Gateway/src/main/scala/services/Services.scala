package services

import akka.grpc.GrpcClientSettings
import authentication.AuthenticationServiceClient
import cache.CacheServiceClient
import post.PostServiceClient

import main.Main.system

object Services {

  case class AuthService(sType: String, hostname: String, port: Int, client: AuthenticationServiceClient, var load: Int = 0)

  object AuthService {
    def apply(sType: String, hostname: String, port: Int): AuthService =
      AuthService(sType, hostname, port,
        AuthenticationServiceClient(GrpcClientSettings.connectToServiceAt(hostname, port).withTls(false)))
  }

  case class CacheService(sType: String, hostname: String, port: Int, client: CacheServiceClient, var load: Int = 0)

  object CacheService {
    def apply(sType: String, hostname: String, port: Int): CacheService =
      CacheService(sType, hostname, port,
        CacheServiceClient(GrpcClientSettings.connectToServiceAt(hostname, port).withTls(false)))
  }

  case class PostService(sType: String, hostname: String, port: Int, client: PostServiceClient, var load: Int = 0)

  object PostService {
    def apply(sType: String, hostname: String, port: Int): PostService =
      PostService(sType, hostname, port,
        PostServiceClient(GrpcClientSettings.connectToServiceAt(hostname, port).withTls(false)))
  }

}
