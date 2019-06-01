package org.demo.example

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import org.demo.example.domain.DomainModel._
import org.demo.example.domain.WalletRepository
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }

import scala.reflect.io.Path

class WalletRepositorySpec extends TestKit(ActorSystem("WalletSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {

    TestKit.shutdownActorSystem(system)
    val path = Path("testStore")
    path.deleteRecursively()
  }

  
  "WalletRepository" should {


    "create new wallet" in {
      val walletId = "wallet_0001"
      val walletCmd = WalletCmd(Some(walletId), "cust1", "Wallet1", 0.0, TransactionType.DEPOSIT, "my first wallet")
      val walletRepo = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepo ! walletCmd

      val walletRes = expectMsgType[Either[String, Wallet]]
      val wallet = walletRes.right.get

      assert(wallet.id === walletId)
      assert(wallet.customerId === "cust1")
      assert(wallet.name === "Wallet1")
      assert(wallet.amount === 0.0)
      assert(wallet.transactionType === TransactionType.DEPOSIT)
      assert(wallet.comment === "my first wallet")
      assert(wallet.active === true)
      Thread.sleep(150)

    }

    "update wallet name and balance" in {
      val walletId = "wallet_0001"
      val walletCmd = WalletCmd(Some(walletId), "cust1", "Wallet1_Updated", 10.0, TransactionType.DEPOSIT, "my first wallet")
      val walletRepo = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepo ! walletCmd

      val walletRes = expectMsgType[Either[String, Wallet]]
      val wallet = walletRes.right.get

      assert(wallet.id === walletId)
      assert(wallet.customerId === "cust1")
      assert(wallet.name === "Wallet1_Updated")
      assert(wallet.amount === 10.0)
      assert(wallet.transactionType === TransactionType.DEPOSIT)
      assert(wallet.comment === "my first wallet")
      assert(wallet.active === true)

      Thread.sleep(150)
    }

    "update wallet balance deposit 100, expected 110" in {
      val walletId = "wallet_0001"
      val walletCmd = WalletBalanceUpdateCmd(walletId, 100.0, TransactionType.DEPOSIT)
      val walletRepo = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepo ! walletCmd

      val walletRes = expectMsgType[Either[String, Wallet]]
      val wallet = walletRes.right.get

      assert(wallet.id === walletId)
      assert(wallet.amount === 110.0)
      assert(wallet.transactionType === TransactionType.DEPOSIT)

      Thread.sleep(150)

    }

    "update wallet balance withdraw 50, expected 60" in {
      val walletId = "wallet_0001"
      val walletCmd = WalletBalanceUpdateCmd(walletId, 50.0, TransactionType.WITHDRAW)
      val walletRepo = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepo ! walletCmd

      val walletRes = expectMsgType[Either[String, Wallet]]
      val wallet = walletRes.right.get

      assert(wallet.id === walletId)
      assert(wallet.amount === 60.0)
      assert(wallet.transactionType === TransactionType.WITHDRAW)

      Thread.sleep(150)
    }

    "update wallet balance withdraw 150, expected withdraw failed" in {
      val walletId = "wallet_0001"
      val walletCmd = WalletBalanceUpdateCmd(walletId, 150.0, TransactionType.WITHDRAW)
      val walletRepo = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepo ! walletCmd

      val walletRes = expectMsgType[Either[String, Wallet]]
      val error = walletRes.left.get

      assert(error === WalletNotifications.INSUFFICIENT_FUNDS)
      Thread.sleep(150)
    }

    "transfer money from one wallet to another" in {
      import scala.concurrent.duration._
      implicit val timeout = Timeout(5 seconds)

      // This wallet has balance 60
      val walletId = "wallet_0008"
      var walletRepoFrom = system.actorOf(WalletRepository.props(walletId),  walletId)
      val walletCmdFrom = WalletCmd(Some(walletId), "cust2", "Wallet1", 100.0, TransactionType.DEPOSIT, "transfer from wallet")
      walletRepoFrom ! walletCmdFrom
      val walletRes = expectMsgType[Either[String, Wallet]]

      // Create new wallet with balance 0.0
      val walletIdTo = "wallet_0009"
      val walletCmdTo = WalletCmd(Some(walletIdTo), "cust3", "Wallet2", 0.0, TransactionType.DEPOSIT, "transfer to wallet")
      var walletRepoTo = system.actorOf(WalletRepository.props(walletIdTo), walletIdTo)
      walletRepoTo ! walletCmdTo
      val walletRes2 = expectMsgType[Either[String, Wallet]]

      walletRepoFrom ! WalletTransferWithdrawCmd(30.0, walletRepoTo, walletIdTo)
      expectMsgType[WalletTransferResult]

      Thread.sleep(400) // delay to update snapshots

      walletRepoFrom = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepoTo = system.actorOf(WalletRepository.props(walletIdTo),  walletIdTo)

      walletRepoFrom ! WalletGetCmd(walletId)
      val walletFrom = expectMsgType[Either[String, Wallet]]
      assert(walletFrom.right.get.amount === 70.0)

      walletRepoTo ! WalletGetCmd(walletIdTo)
      val walletTo = expectMsgType[Either[String, Wallet]]
      assert(walletTo.right.get.amount === 30.0)

      Thread.sleep(150)
    }

    "transfer money from one wallet to another failed" in {
      import scala.concurrent.duration._
      implicit val timeout = Timeout(5 seconds)

      // This wallet has balance 60
      val walletId = "wallet_00018"
      var walletRepoFrom = system.actorOf(WalletRepository.props(walletId),  walletId)
      val walletCmdFrom = WalletCmd(Some(walletId), "cust2", "Wallet1", 10.0, TransactionType.DEPOSIT, "transfer from wallet")
      walletRepoFrom ! walletCmdFrom
      val walletRes = expectMsgType[Either[String, Wallet]]

      // Create new wallet with balance 0.0
      val walletIdTo = "wallet_00019"
      val walletCmdTo = WalletCmd(Some(walletIdTo), "cust3", "Wallet2", 0.0, TransactionType.DEPOSIT, "transfer to wallet")
      var walletRepoTo = system.actorOf(WalletRepository.props(walletIdTo),  walletIdTo)
      walletRepoTo ! walletCmdTo
      val walletRes2 = expectMsgType[Either[String, Wallet]]

      val transferFuture = walletRepoFrom ! WalletTransferWithdrawCmd(30.0, walletRepoTo, walletIdTo)

      val result = expectMsgType[WalletTransferResult]

      assert(result.msg === WalletNotifications.TRANSFER_FAILED)
      Thread.sleep(150)

      walletRepoFrom = system.actorOf(WalletRepository.props(walletId),  walletId)
      walletRepoTo = system.actorOf(WalletRepository.props(walletIdTo),  walletIdTo)

      walletRepoFrom ! WalletGetCmd(walletId)
      val walletFrom = expectMsgType[Either[String, Wallet]]
      assert(walletFrom.right.get.amount === 10.0)

      walletRepoTo ! WalletGetCmd(walletIdTo)
      val walletTo = expectMsgType[Either[String, Wallet]]
      assert(walletTo.right.get.amount === 0.0)

    }

  }
}
