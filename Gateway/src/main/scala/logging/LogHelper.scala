package logging

import com.typesafe.config.ConfigFactory
import main.Main.getCurrentTime
import org.apache.logging.log4j.scala.Logger

trait LogHelper {
  System.setProperty("sType", "gateway")

  System.setProperty("grpcPort", ConfigFactory.load.getString("grpcPort"))

  System.setProperty("host", ConfigFactory.load.getString("hostname"))

  protected val logger: Logger = Logger(getClass)
}

object LogHelper extends LogHelper {

  def logMessage(message: String): Unit = {
    println(s"[$getCurrentTime]: " + message)
    logger.info(message)
  }

  def logError(e: Throwable): Unit = {
    e.printStackTrace()
    logger.error(e.getMessage)
  }

}