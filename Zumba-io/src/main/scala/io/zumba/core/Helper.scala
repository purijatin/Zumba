package io.zumba.core

import akka.actor.ActorRef
import akka.actor.Props
import io.zumba.config.HelperConfig
import io.zumba.config.HelperConfig
import akka.actor.Actor
import scala.collection.concurrent.TrieMap
import akka.actor.Cancellable
import scala.concurrent.duration.FiniteDuration

object Helper {
  /**
   * A marker trait which signifies that this is message is an internal Zumba specific message
   */
  trait Internal

  /**
   * The message that is sent to Replicate
   * @param key The Key pair
   * @param value The Value of the key. If None, it is assumed as the key is no longer required and is removed
   * @param id The unique id of the transaction
   * @param remote The actors to whice the message is to be sent
   * @param Maximum duration after which the actor marks it as a failure
   */
  case class Replicate(key: String, valueOption: Option[String], id: Long, remote: Set[ActorRef], timeout: FiniteDuration) extends Internal
  
  /**
   * The message returned by helper when the Replication is successful by secondary
   * @param id The id of the transaction
   */
  case class Replicated(id: Long) extends Internal
  /**
   * The failure message received if the replication is unsuccessful by the secondary
   * @param id The id of the transaction
   * @param error Error String on why it is a failure
   */
  case class RepFailure(id: Long, error: String) extends Internal

  private[Helper] def toReplicated(rep: Replicate) = Replicated(rep.id)

  /**
   * The message that is sent to secondary zumba instance
   * @param key The Key pair
   * @param value The Value of the key. If None, it is assumed as the key is no longer required and is removed
   * @param id The unique id of the transaction
   */
  case class Snapshot(key: String, valueOption: Option[String], id: Long) extends Internal
  /**
   * The message received when the Replication is successful by secondary
   * @param id The id of the transaction
   */
  case class SnapshotAck(id: Long) extends Internal
  
  case class Validate(id: Long, retry: Int) extends Internal {
    if (retry < 0)
      throw new IllegalArgumentException(s"retry Count cannot be less than zero. Given: $retry")
  }

  def props(settings: HelperConfig) = Props(new Helper(settings))

}

/**
 * The actor which takes complete responsibility of the following:
 * 	1) Sends a message to all the secondary instances and awaits the message from each one of them
 *  2) If a secondary does not respond in speculated time, then it is marked as failure and a re-attempt is made
 *  3) Re-attempts are made for the times mentioned in settings
 *  4) When all the re-attempts fail then message `RepFailure` is returned to the main sender
 *  
 * 
 */
class Helper(config: HelperConfig) extends Actor {
  import Helper._
  import config._

  /**
   * A Map of
   * Transaction Id -> [Message that was sent, client, Cancellable of scheduler]
   */
  val map = new TrieMap[Long, (Replicate, ActorRef, Cancellable)]()

   def receive = {
      case r @ Replicate(key, opt, id, remote, timeout) =>
        if (map.contains(id)) {
          //Transaction id needs to be unique
          val err = "Serious Error. 2 Transactions with same key found. Critical bug. Replicate: " + r + " Map contents: " + map(id)
          println(err)
          println("Absconding Operation")
          sender ! RepFailure(id, err)
        } else {
          remote.foreach(_ ! Snapshot(key, opt, id))
          val cancellable = context.system.scheduler.scheduleOnce(timeout, self, Validate(id, retryTimes))(context.dispatcher)
          map += (id -> (r, sender, cancellable))
        }

      case s @ SnapshotAck(id) =>
        map.get(id) match {
          case Some(obj @ (rep, _sender, cancel)) =>
            val left = rep.remote - sender
            if (left.isEmpty) {
              cancel.cancel
              _sender ! toReplicated(rep)
            }
            map += (id -> (Replicate(rep.key, rep.valueOption, rep.id, left, rep.timeout), _sender, cancel))
          case None => //do nothing 
        }

      /**
       *  If any retries left, tries again. Else acknowledges the user and removes history.
       *  Later we can make this operation asynchronous.
       *  This is important in stabilizing the load under high load as the debt is cleared and the user is acknowledged asap
       *
       *  TODO: This looks possible by using mutable.Tuple in Map and only changing one element at a time. This
       *  way it will be safe running it in parallel
       */
      case v @ Validate(id, retry) =>
        map.get(id) match {
          case Some(obj @ (rep, _sender, cancel)) =>
            val _remote = rep.remote
            if (_remote.isEmpty) {
              //already completed. Ideally this should not happen
            } else {
              if (retry >= 1) {
                _remote.foreach(_ ! Snapshot(rep.key, rep.valueOption, rep.id))
                val cancellable = context.system.scheduler.scheduleOnce(retryAfter, self, Validate(id, retry - 1))(context.dispatcher)
                map += (id -> ( rep, _sender, cancellable))
              } else { //no more retries
                _sender ! RepFailure(id, "Timeout: " + _remote)
                map -= id
              }
            }
          case None => //already completed 
        }
    }
}