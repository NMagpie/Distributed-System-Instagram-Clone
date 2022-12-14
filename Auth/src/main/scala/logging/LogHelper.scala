package logging

import com.typesafe.config.ConfigFactory
import main.Main.getCurrentTime
import org.apache.logging.log4j.scala.Logger

trait LogHelper {
  System.setProperty("sType", "auth")

  System.setProperty("port", ConfigFactory.load.getString("port"))

  System.setProperty("host", ConfigFactory.load.getString("hostname"))

  System.setProperty("logHost", ConfigFactory.load.getString("logHost"))

  System.setProperty("logPort", ConfigFactory.load.getString("logPort"))

  protected var logger : Logger = _

  protected val loggerConnected: Boolean = try {
    logger = Logger(getClass)
    true
  } catch { case e: Exception =>
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

}
