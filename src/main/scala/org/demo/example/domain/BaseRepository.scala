package org.demo.example.domain

import akka.actor.{ActorLogging, ReceiveTimeout}
import akka.persistence.PersistentActor
import org.demo.example.domain.DomainModel.ActorShutdown

import scala.concurrent.duration._
trait BaseRepository extends PersistentActor with ActorLogging {

  val actorTimeout = context.system.settings.config.getInt("actor.timeout")
  context.setReceiveTimeout(actorTimeout millisecond)

  def timeoutReceive:Receive = {
    case ReceiveTimeout =>
      log.info(s"ReceiveTimeout message is received by ${self.path}")
      context.stop(self)
    case ActorShutdown => context.stop(self)
    case "Test" => println("Actor is alive")
    case cmd @ _ => log.error(s"Unknown command $cmd")
  }

  def receiveCommandBehavior: Receive

  override def receiveCommand: Receive = receiveCommandBehavior orElse timeoutReceive
}

