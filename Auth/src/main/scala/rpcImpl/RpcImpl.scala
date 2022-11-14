package rpcImpl

import akka.grpc.GrpcServiceException
import akka.pattern.ask
import akka.util.Timeout
import db.DBConnector.connection
import io.grpc.{Status => grpcStatus}
import logging.LogHelper.logMessage
import main.Main.system.dispatcher
import services.authentication._
import services.{Empty, Status}
import taskLimiter.TlActor._
import main.Main.taskLimiter

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class RpcImpl extends AuthenticationService {

  implicit val timeout: Timeout = Timeout(10 seconds)

  override def isAuth(in: AuthKey): Future[AuthBool] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    if (result.result) try {

      logMessage(s"[${result.load} of ${result.limit}] {isAuth}\t${in.key}")

      val key = in.key

      val statement = connection.createStatement
      val rs = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM auth_db.users WHERE key ='%s')".format(key))
      var exists = false
      rs.next
      exists = rs.getString(1) == "1"

      taskLimiter ! Free
      Future(AuthBool(exists))

    } catch {
      case e: Exception =>
        taskLimiter ! Free
        Future.failed(e)
    } else
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))
  }

  override def auth(in: UserData): Future[Result] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    if (result.result) try {

      logMessage(s"[${result.load} of ${result.limit}] {auth}\t${in.encode}")

      val Array(username, password) = new String(Base64.getDecoder.decode(in.encode), StandardCharsets.UTF_8).split(":")
      val statement = connection.createStatement

      val rs = statement.executeQuery("SELECT auth_db.users.key FROM auth_db.users WHERE username = '%s' and password = '%s'".format(username, password))
      if (rs.next) {
        //println("User was found, sending key")
        taskLimiter ! Free
        Future(Result(Option(rs.getString("key")), None))
      } else {
        //println("User was not found!")
        taskLimiter ! Free
        Future(Result(None, Option("User was not found!")))
      }
    } catch {
      case e: Exception =>
        taskLimiter ! Free
        Future.failed(e)
    } else
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))
  }

  override def register(in: UserData): Future[Result] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    if (result.result) try {

      logMessage(s"[${result.load} of ${result.limit}] {register}\t${in.encode}")

      val Array(username, password) = new String(Base64.getDecoder.decode(in.encode), StandardCharsets.UTF_8).split(":")
      val statement = connection.createStatement
      val rs = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM auth_db.users WHERE username ='%s')".format(username))
      var exists = false
      rs.next
      exists = rs.getString(1) == "1"

      if (exists) {
        //println("User was found, cannot register!")
        taskLimiter ! Free
        Future(Result(None, Option("User already exists!")))
      } else {
        statement.execute("INSERT INTO `auth_db`.`users` (`username`, `password`) VALUES ('%s', '%s')".format(username, password))
        val rs = statement.executeQuery("SELECT auth_db.users.key FROM auth_db.users WHERE username = '%s'".format(username))

        rs.next
        //println("User was not found, registering")

        taskLimiter ! Free
        Future(Result(Option(rs.getString("key")), None))
      }

    } catch {
      case e: Exception =>
        taskLimiter ! Free
        Future.failed(e)
    } else
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))

  }

  override def getStatus(in: Empty): Future[Status] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    if (result.result) try {

      logMessage(s"[${result.load} of ${result.limit}] {getStatus}")

      val status = s"Server Type: Authentication\nHostname: ${main.Main.hostname}\nPort: ${main.Main.port}"

      taskLimiter ! Free
      Future(Status(status))

    } catch {
      case e: Exception =>
        taskLimiter ! Free
        Future.failed(e)
    } else
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))
  }

  override def whoIsThis(in: AuthKey): Future[User] = {

    val future = taskLimiter ? TrySend

    val result = Await.result(future, timeout.duration).asInstanceOf[Answer]

    if (result.result) try {

      logMessage(s"[${result.load} of ${result.limit}] {whoIsThis}\t${in.key}")

      val statement = connection.createStatement
      val rs = statement.executeQuery("SELECT username FROM auth_db.users WHERE users.key ='%s'".format(in.key))

        taskLimiter ! Free

      if (rs.next) {
        Future(User(rs.getString(1)))
      } else {
        Future(User("null"))
      }

    } catch {
      case e: Exception =>
        taskLimiter ! Free
        Future.failed(e)
    } else
      Future.failed(new GrpcServiceException(grpcStatus.UNKNOWN.withDescription("429 Too many requests")))
  }

}
