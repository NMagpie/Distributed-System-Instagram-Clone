package taskLimiter

import akka.actor.Actor

object TlActor {
  case class TrySend()

  case class Answer(result: Boolean, limit: Int, load: Int)

  case class Free()
}

class TlActor(limit: Int) extends Actor {

  import TlActor._

  var counter = 0

  override def receive: Receive = {
    case TrySend =>

      val result = counter < limit

      if (result)
        counter = counter + 1

      val reply = Answer(result, limit, counter)

      sender() ! reply

    case Free =>
      counter = counter - 1
  }
}
