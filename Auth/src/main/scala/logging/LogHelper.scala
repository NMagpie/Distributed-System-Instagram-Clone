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

  protected var logger: Logger = _

    try {
    logger = Logger(getClass)
  } catch { case e: Exception => e.printStackTrace()
    }

}

object LogHelper extends LogHelper {

  def logMessage(message: String): Unit = {
    println(s"[$getCurrentTime]: " + message)
    if (logger != null)
      logger.info(message)
  }

  def logError(e: Throwable): Unit = {
    e.printStackTrace()
    if (logger != null)
      logger.error(e.getMessage)
  }

}
