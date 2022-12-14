package logging

import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.scala.Logger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

trait LogHelper {
  System.setProperty("sType", "gateway")

  System.setProperty("grpcPort", ConfigFactory.load.getString("grpcPort"))

  System.setProperty("host", ConfigFactory.load.getString("hostname"))

  System.setProperty("logHost", ConfigFactory.load.getString("logHost"))

  System.setProperty("logPort", ConfigFactory.load.getString("logPort"))

  protected var logger: Logger = _

  protected val loggerConnected: Boolean = try {
    logger = Logger(getClass)
    true
  } catch {
    case e: Exception =>
      e.printStackTrace()
      false
  }

}

object LogHelper extends LogHelper {

  def logMessage(message: String): Unit = {
    println(s"[$getCurrentTime]: " + message)
    if (loggerConnected)
      logger.info(message)
  }

  def logError(e: Throwable): Unit = {
    e.printStackTrace()
    if (loggerConnected)
      logger.error(e.getMessage)
  }

  def getCurrentTime: String = {
    val timestamp = LocalDateTime.now()
    DateTimeFormatter.ofPattern("HH:mm:ss").format(timestamp)
  }

}
