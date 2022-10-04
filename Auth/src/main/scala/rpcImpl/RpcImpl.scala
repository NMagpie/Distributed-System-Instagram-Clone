package rpcImpl

import authentication._

import scala.concurrent.Future
import main.Main.{client, executionContext, getCurrentTime}
import db.DBConnector.connection
import discovery.Message

import java.nio.charset.StandardCharsets
import java.util.Base64

class RpcImpl extends AuthenticationService {

  override def isAuth(in: AuthKey): Future[AuthBool] = {

    println(s"[$getCurrentTime]: {isAuth}\t${in.key}")

    val key = in.key

    val statement = connection.createStatement
    val rs = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM auth_db.users WHERE key ='%s')".format(key))
    var exists = false
    rs.next
    exists = if (rs.getString(1) == "1") true else false

    Future(AuthBool(exists))
  }

  override def auth(in: UserData): Future[Result] = {

    println(s"[$getCurrentTime]: {auth}\t${in.encode}")

    val Array(username, password) = new String(Base64.getDecoder.decode(in.encode), StandardCharsets.UTF_8).split(":")
    val statement = connection.createStatement

    val rs = statement.executeQuery("SELECT auth_db.users.key FROM auth_db.users WHERE username = '%s' and password = '%s'".format(username, password))
    if (rs.next) {
      println("User was found, sending key")
      Future(Result(Option(rs.getString("key")), None))
    } else {
      println("User was not found!")
      Future(Result(None, Option("User was not found!")))
    }

  }

  override def register(in: UserData): Future[Result] = {

    println(s"[$getCurrentTime]: {register}\t${in.encode}")

    val Array(username, password) = new String(Base64.getDecoder.decode(in.encode), StandardCharsets.UTF_8).split(":")
    val statement = connection.createStatement
    val rs = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM auth_db.users WHERE username ='%s')".format(username))
    var exists = false
    rs.next
    exists = if (rs.getString(1) == "1") true else false

    if (exists) {
      println("User was found, cannot register!")
      Future(Result(None, Option("User already exists!")))
    } else {
      statement.execute("INSERT INTO `auth_db`.`users` (`username`, `password`) VALUES ('%s', '%s')".format(username, password))
      val rs = statement.executeQuery("SELECT auth_db.users.key FROM auth_db.users WHERE username = '%s'".format(username))

      rs.next
      println("User was not found, registering")
      Future(Result(Option(rs.getString("key")), None))
    }

  }

  override def getStatus(in: Empty): Future[Status] = {

    println(s"[$getCurrentTime]: {getStatus}")

    val status = s"Server Type: Authentication\nHostname: ${main.Main.hostname}\nPort: ${main.Main.port}"
    Future(Status(status))
  }

  override def whoIsThis(in: AuthKey): Future[User] = {

    println(s"[$getCurrentTime]: {whoIsThis}\t${in.key}")

    val statement = connection.createStatement
    val rs = statement.executeQuery("SELECT username FROM auth_db.users WHERE users.key ='%s'".format(in.key))

    if (rs.next) {
      Future(User(rs.getString(1)))
    } else {
      Future(User("null"))
    }
  }

}
