package io.zumba.core.rdt

import akka.actor.ActorSystem
import scala.concurrent.duration.FiniteDuration
import akka.testkit.TestProbe
import akka.actor.{ ActorRef, Actor }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import akka.actor.Props
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import scala.concurrent.duration._
import akka.actor.ActorDSL._
import scala.language.postfixOps
import io.zumba.core.Helper._
import io.zumba.config.HelperConfig

object Tools {
  class TestRefWrappingActor(val probe: TestProbe) extends Actor {
    def receive = { case msg => probe.ref forward msg }
  }

  /**
   * Returns a Set of given number of probes
   */
  def getProbe(n: Int)(implicit system: ActorSystem) = (for (i <- 1 to n) yield TestProbe()) toSet

  def act = { new Act { become { case x @ Snapshot(key, _, id) => sender ! SnapshotAck(id) } } }
  def getRemote(n: Int)(implicit system: ActorSystem) = (for (i <- 1 to n) yield actor(system, "" + i) { act }) toSet
  
  def settings(_retry: Int, _retryAfter:FiniteDuration) = new io.zumba.config.HelperConfig {
    override def retryTimes = _retry
    override def retryAfter = _retryAfter
  }
  
//  def globalSettings(settings: HelperConfig, _helpers:Int) = new _akka.compare.RobustActor2.GlobalSettings{
//    override def retry = settings.retry
//    override def retryAfter: FiniteDuration = settings.retryAfter
//    override def helpers: Int = _helpers
//  }
}

/**
 * This is a utility to mix into your tests which provides convenient access
 * to a given replica. It will keep track of requested updates and allow
 * simple verification. See e.g. Step 1 for how it can be used.
 */
trait Tools { this: TestKit with FunSuite with ShouldMatchers with ImplicitSender =>

  import Tools._

  def probeProps(probe: TestProbe): Props = Props(classOf[TestRefWrappingActor], probe)

  class Session(val probe: TestProbe, val replica: ActorRef) {
    

    @volatile private var seq = 0L
    private def nextSeq: Long = {
      val next = seq
      seq += 1
      next
    }

    @volatile private var referenceMap = Map.empty[String, Option[String]]

    def waitAck(key: String, s: Long): Unit = probe.expectMsg(Replicated(s))

    def waitFailed(s: Long): Unit = probe.expectMsgPF(Duration.Undefined, "") {
      case x @ RepFailure(id, reason) if (s == id) =>
    }

    def set(key: String, value: Option[String], remote:Set[ActorRef], retryAfter:FiniteDuration = 1 second): Long = {
      referenceMap += key -> value
      val s = nextSeq
      probe.send(replica, Replicate(key, value, s, remote, retryAfter))
      s
    }

    def setAcked(key: String, value: Option[String], remote:Set[ActorRef]): Unit = waitAck(key, set(key, value, remote))

    def nothingHappens(duration: FiniteDuration): Unit = probe.expectNoMsg(duration)
  }

  def session(replica: ActorRef)(implicit system: ActorSystem) = new Session(TestProbe(), replica)

}