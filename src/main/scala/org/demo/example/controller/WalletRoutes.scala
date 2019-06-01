package org.demo.example.controller

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Directives.{ complete, _ }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.util.Timeout
import org.demo.example.domain.DomainModel.{ WalletBalanceUpdate, _ }
import org.demo.example.utils.JsonSupport
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WalletRoutes extends JsonSupport {

  implicit def timeout: Timeout
  implicit def system: ActorSystem
  def walletService: ActorRef

  lazy val walletRoutes: Route =
    pathPrefix("api") {
      get {
        path("wallets") {
          val resultFuture = (walletService ? AllWallets).mapTo[Seq[Wallet]]
          val entityFuture = resultFuture.map { s =>
            val walletsMap = scala.collection.mutable.Map.empty[String, Wallet]
            s.foreach(w => {
              val wallet: Option[Wallet] = walletsMap.get(w.id)
              wallet match {
                case None => walletsMap += (w.id -> w)
                case Some(ww) => {
                  val updatedWallet = w.copy(amount = w.amount + ww.amount)
                  walletsMap += (updatedWallet.id -> updatedWallet)
                }
              }
            })
            HttpEntity(
              ContentTypes.`application/json`,
              Wallets(walletsMap.map { case (_, v) => v }.toSeq).toJson.compactPrint)
          }
          complete(entityFuture)
        } ~
          path("wallets" / Segment) { walletId =>
            val resp: Future[Either[String, Wallet]] = (walletService ? WalletGet(WalletGetCmd(walletId)))
              .mapTo[Either[String, Wallet]]
            complete(resp)
          } ~
          path("wallets" / Segment / "transactions") { walletId =>
            val list: Future[Seq[Wallet]] = (walletService ? WalletTransactions(walletId)).mapTo[Seq[Wallet]]
            val entityFuture = list.map { s =>
              HttpEntity(
                ContentTypes.`application/json`,
                Transactions(s).toJson.compactPrint)
            }
            complete(entityFuture)
          }
      } ~
        post {
          path("wallets") {
            entity(implicitly[FromRequestUnmarshaller[WalletUpdate]]) { walletCmd =>
              val wallet = (walletService ? walletCmd).mapTo[Either[String, Wallet]]
              complete(wallet)
            }
          } ~
            path("wallets" / "balance") {
              entity(implicitly[FromRequestUnmarshaller[WalletBalanceUpdate]]) { walletCmd =>
                val wallet = (walletService ? walletCmd).mapTo[Either[String, Wallet]]
                complete(wallet)
              }
            } ~
            path("wallets" / "transfer") {
              entity(implicitly[FromRequestUnmarshaller[WalletTransfer]]) { walletCmd =>
                val transferResult = (walletService ? walletCmd).mapTo[WalletTransferResult]
                complete(transferResult)
              }
            } ~
            path("wallets" / "status") {
              entity(implicitly[FromRequestUnmarshaller[WalletStatusUpdate]]) { walletCmd =>
                val wallet = (walletService ? walletCmd).mapTo[Either[String, Wallet]]
                complete(wallet)
              }
            }
        }
    }

}
