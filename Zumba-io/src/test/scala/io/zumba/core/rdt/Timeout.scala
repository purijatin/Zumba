package io.zumba.core.rdt

import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{ BeforeAndAfterAll, FunSuite }
import scala.concurrent.duration._
import akka.actor.ActorDSL._
import scala.language.postfixOps
import akka.actor.Props
import akka.actor.Actor
import io.zumba.core.Helper
import io.zumba.core.Helper._

/**
 * Tests:
 * 1) Replies based on all actor reponse
 * 	Basic functioning
 * 	Timeout
 * 	Reattempts
 */
class Timeout extends TestKit(ActorSystem("Timeout"))
  with FunSuite
  with BeforeAndAfterAll
  with ShouldMatchers
  with ImplicitSender
  with Tools {
  import Tools._

  override def afterAll(): Unit = {
    system.shutdown()
  }

  implicit val _system = system

  test("Response should come when acked by all actors") {
    val remote = getProbe(20)
    val node = system.actorOf(Props(new Helper(settings(0, 100 milliseconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L, remote.map(x => x.ref), 100 milliseconds))
    remote.foreach(x => x.expectMsg(150 milliseconds, Snapshot("1", Some("1"), 1L)))
    remote.foreach(x => x.send(node, SnapshotAck(1L)))
    main.expectMsg(200 milliseconds, Replicated(1L))
  }

  test("It should retry if it times out") {
    val remote = getProbe(1)
    val node = system.actorOf(Props(new Helper(settings(1, 100 milliseconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L, remote.map(x => x.ref), 100 milliseconds))
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(150)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
  }
  
  test("It should retry after the time mentioned"){
    val remote = getProbe(1)
    val node = system.actorOf(Props(new Helper(settings(1, 100 milliseconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L, remote.map(x => x.ref), 100 milliseconds))
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(120)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
  }

  test("It should retry the number of times mentioned") {
    val remote = getProbe(1)
    val node = system.actorOf(Props(new Helper( settings(5, 100 milliseconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L, remote.map(x => x.ref), 100 milliseconds))
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(110)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(110)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(110)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(110)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
  }

  test("It should not retry if zero given") {
    val remote = getProbe(1)
    val node = system.actorOf(Props(new Helper( settings(0, 100 milliseconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L,remote.map(x => x.ref), 100 milliseconds))
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(110)
    remote.foreach(x => x.expectNoMsg(2 seconds))
  }

  test("Timeout") {
    val remote = getProbe(1)
    val node = system.actorOf(Props(new Helper(settings(0, 1 seconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L,remote.map(x => x.ref), 1 second))
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    main.expectMsgAllClassOf(2 seconds, classOf[RepFailure])
  }

  test("All attempts if failed results in RepFailure"){
    val remote = getProbe(1)
    val node = system.actorOf(Props(new Helper(settings(5, 100 milliseconds))))
    val main = TestProbe()
    main.send(node, Replicate("1", Some("1"), 1L,remote.map(x => x.ref), 100 milliseconds))
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(150)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(150)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(150)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(150)
    remote.foreach(x => x.expectMsg(1 seconds, Snapshot("1", Some("1"), 1L)))
    Thread.sleep(150)
    main.expectMsgAllClassOf(100 milliseconds, classOf[RepFailure])
  }
  
  test("Random Test"){
    import akka.actor.ActorDSL
    def sample(i:Int) = actor(system, "Random"+i)(new Act { become { case Snapshot(_,_, id) => sender ! SnapshotAck(id) } })
    val set = (for(i<-1 to 100) yield sample(i)) toSet
    val node = system.actorOf(Props(new Helper(settings(1, 1 second))))
    val main = TestProbe()
    val total = 100
    for(i<- 1 to total) main.send(node, Replicate(""+i, Some(""+i), i,set, 1 second))
    for(i<- 1 to total) main.expectMsgAnyClassOf(1 second, classOf[Replicated], classOf[RepFailure])
  }
  
  test("Random Test-1"){
    import akka.actor.ActorDSL._
    
    def sample(i:Int) = actor(system, "RandomNext"+i)(new Act { become { case Snapshot(_,_, id) => sender ! SnapshotAck(id) } })
    val set = (for(i<-1 to 10000) yield sample(i)) toSet
    val node = system.actorOf(Props(new Helper(settings(1, 3 seconds))))
    val total = 5
    var i = 0
    class _Act extends Actor{
      def receive = { 
      case r @ Replicate(_,_,_,_,_) => node ! r; 
      case r @ Replicated(_) => println(r); i+=1; 
      case r @ RepFailure(_,_) => println(r); i+=1
      case x => println("Unknown "+x)
      }
    }
    val main = system.actorOf(Props(new _Act))
    for(i<- 1 to total){
      main ! Replicate(""+i, Some(""+i), i,set, 3 seconds)
    }
    Thread.sleep(1100)
    if(i!=total){
      println(i)
      assert(false)
    }
  }

}