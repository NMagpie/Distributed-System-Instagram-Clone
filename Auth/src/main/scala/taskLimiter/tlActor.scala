package taskLimiter

import akka.actor.Actor

object tlActor {
  case class TrySend()

  case class Answer(result: Boolean, limit: Int, load: Int)

  case class Free()
}

class tlActor(limit: Int) extends Actor {

  import tlActor._

  var counter = 0

  override def receive: Receive = {
    case TrySend =>

      //println(counter + " " + limit)

      val result = counter < limit

      if (result)
        counter = counter + 1

      val reply = Answer(result, limit, counter)

      sender() ! reply

    case Free =>
      counter = counter - 1
  }
}
