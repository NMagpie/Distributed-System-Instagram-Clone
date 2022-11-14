package db

import com.typesafe.config.ConfigFactory
import logging.LogHelper.{logError, logMessage}

import java.sql.{Connection, DriverManager}

object DBConnector {
  val url: String = ConfigFactory.load.getString("dbUrl")
  val driver = "com.mysql.jdbc.Driver"
  val username: String = ConfigFactory.load.getString("username")
  val password: String = ConfigFactory.load.getString("password")
  var connection:Connection = _
  try {
    Class.forName(driver)
    connection = DriverManager.getConnection(url, username, password)
    logMessage("DB Connected successfully!")
  } catch {
    case e: Exception => logError(e)
  }

  def closeConnection(): Unit =
      connection.close()
}
