package routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import logging.LogHelper.logMessage
import main.Main.{discovery, discoveryHost, discoveryPort, grpcPort, hostname}
import services.Empty

import scala.language.postfixOps

object GetStatus {

  import Util._

  val getStatus: Route = path("status") {
    formFields("service") {
      service =>
        val Array(sType, sHostname, sPort) = service.split(":")

        val intPort = sPort.toInt

        logMessage(s"{getStatus}\t$service")

        sType match {
          case "gateway" =>

            val status =
              if (sHostname == hostname && intPort == grpcPort)
                s"{\n\"message\": \"Server Type: Gateway\\nHostname: $hostname\\nPort: $grpcPort\""
              else
                "Such service does not exist"
            complete(HttpEntity(ContentTypes.`application/json`, status))

          case "cache" =>

            val cacheServiceFiltered = filterCache(sHostname, intPort)

            if (cacheServiceFiltered.nonEmpty) {
              val cacheService = cacheServiceFiltered(0)
              response(call(cacheService, {
                cacheService.client.getStatus(Empty())
              }))
            } else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

          case "auth" =>

            val authServiceFiltered = filterAuth(sHostname, intPort)

            if (authServiceFiltered.nonEmpty) {
              val authService = authServiceFiltered(0)
              response(call(authService, {
                authService.client.getStatus(Empty())
              }))
            } else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

          case "post" =>

            val postServiceFiltered = filterPost(sHostname, intPort)

            if (postServiceFiltered.nonEmpty) {
              val postService = postServiceFiltered(0)
              response(call(postService, {
                postService.client.getStatus(Empty())
              }))
            } else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

          case "discovery" =>

            if (sHostname == discoveryHost && intPort == discoveryPort)
              response(discovery.getStatus(Empty()))
            else
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Such service does not exist"))

        }

    }

  }

}
