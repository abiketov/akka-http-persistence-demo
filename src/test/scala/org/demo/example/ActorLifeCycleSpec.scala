package org.demo.example

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import org.demo.example.domain.CustomerRepository
import org.demo.example.domain.DomainModel.CustomerCmd
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout

class ActorLifeCycleSpec extends TestKit(ActorSystem("ActorLifeCycleSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  import ActorLifeCycleSpec._

  "In this test perform ActorSelection" should {

    "return correct single actor ref" in {

      val map = scala.collection.mutable.Map.empty[String, Int]
      map += ("1" -> 1)
      map += ("2" -> 2)

      map -= "1"

      system.actorOf(Props[ChildActor], "child")
      val lookupActor = system.actorOf(Props[ActorLookup], "lookup")
      val parent = system.actorOf(Props(new ParentActor(lookupActor)))

      parent ! "CallChild"

      Thread.sleep(100)
    }
  }

  def toActor(ref: ActorRef, msg: String): Unit = {
    println("Before calling actor")
    ref ! msg
  }

}

object ActorLifeCycleSpec {

  case class ActorLookupCmd(actorName: String)
  case class GetChild(ref: ActorRef)
  case object DoWork

  class ChildActor() extends Actor {

    override def receive: Receive = {
      case DoWork => println("doing some work...")
    }
  }

  class ParentActor(lookupActor: ActorRef) extends Actor with ActorLogging {

    override def receive: Receive = {

      case "CallChild" => lookupActor ! ActorLookupCmd("child")
      case GetChild(ref) =>
        log.info(s"I'm worker ${ref.path}")
        ref ! DoWork
    }

  }

  class ActorLookup extends Actor with ActorLogging {

    implicit val dispatcher = context.system.dispatcher

    def receive = {

      case ActorLookupCmd(name) =>
        log.info("Received lookup request")
        val actorSelection: ActorSelection = context.actorSelection(s"/user/$name")
        val senderRef = sender
        val actorRefFuture: Future[ActorRef] = actorSelection.resolveOne(300 milliseconds)
        actorRefFuture.onComplete {
          case Success(actorRef) =>
            log.info("Process future success")
            log.info(s"Lookup actor path ${actorRef.path}")
            senderRef ! GetChild(actorRef)
          case Failure(_) =>
            log.info("Process future failure")
            val ref = context.system.actorOf(Props[ChildActor], name)
            log.info(s"Found new actor ${ref.path}")
            senderRef ! GetChild(ref)
        }
    }
  }

}
