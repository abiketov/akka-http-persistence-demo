package org.demo.example.controller

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.demo.example.service.{ CustomerServiceActor, WalletServiceActor }
import akka.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object RestApiServer extends App with WalletRoutes with CustomerRoutes {

  implicit val timeout = Timeout(5 seconds)
  implicit val system = ActorSystem("WalletDemo")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val customerService = system.actorOf(Props[CustomerServiceActor], "customerService")
  val walletService = system.actorOf(WalletServiceActor.props(customerService), "walletService")

  Http().bindAndHandle(walletRoutes ~ customerRoutes, "localhost", 8080)

}
