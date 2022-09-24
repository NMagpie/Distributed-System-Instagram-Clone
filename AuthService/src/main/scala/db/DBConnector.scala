package db

import com.typesafe.config.ConfigFactory

import java.sql.{Connection, DriverManager}

object DBConnector {
  val url = ConfigFactory.load.getString("dbUrl")
  val driver = "com.mysql.jdbc.Driver"
  val username = ConfigFactory.load.getString("username")
  val password = ConfigFactory.load.getString("password")
  var connection:Connection = _
  try {
    Class.forName(driver)
    connection = DriverManager.getConnection(url, username, password)
    println("DB Connected successfully!")
  } catch {
    case e: Exception => e.printStackTrace()
  }

  def closeConnection(): Unit =
      connection.close()
}
