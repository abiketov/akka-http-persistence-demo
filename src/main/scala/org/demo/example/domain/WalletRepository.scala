package org.demo.example.domain

import java.util.Date


import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.persistence.journal.Tagged
import akka.persistence.{ PersistentActor, SaveSnapshotSuccess, SnapshotOffer }
import org.demo.example.domain.DomainModel._

object WalletRepository {
  def props(id: String) = Props(new WalletRepository(id))
}

class WalletRepository(walletId: String) extends BaseRepository {

  private var customerId = ""
  private var name = ""
  private var amount = 0.0
  private var timeStamp = new Date
  private var transactionType = ""
  private var comment = ""
  private var active = true
  private var fromWalletRef: Option[ActorRef] = None  // to notify on successful transfer deposit
  private var serviceActorRef: Option[ActorRef] = None // to notify on transfer result

  override def persistenceId: String = walletId


  override def receiveCommandBehavior: Receive = {

    case WalletCmd(_, customerId, name, amt, transactionType, comment) =>
      val op = checkFundsAvailable(amt, transactionType)
      if (op != 0 && active) {
        val wallet = Wallet(persistenceId, customerId, name, amt * op, new Date, transactionType, comment)
        log.info(s"Wallet created/updated $wallet")
        performPersist(wallet, Some(sender()), None, true)
      } else {
        sender() ! Left(WalletNotifications.INSUFFICIENT_FUNDS)
      }

    // Handle Deposit/Withdraw command
    case WalletBalanceUpdateCmd(_, amt, transactionType) =>
      log.info(s"Deposit/Withdraw funds from wallet $persistenceId amount $amt")
      val op = checkFundsAvailable(amt, transactionType)
      if (op != 0 && active) {
        performPersist(
          Wallet(persistenceId, customerId, name, amt * op, new Date, transactionType, s"Balance update $amt"),
          Some(sender()), None, true)
      } else {
        sender() ! Left(WalletNotifications.INSUFFICIENT_FUNDS)
      }

    // Return Wallet instance to the requester
    case WalletGetCmd(walletId) =>
      log.info(s"Loading wallet with id $walletId")
      if (customerId != "") {
        log.info(s"Loaded wallet ${getCurrentState}")
        sender() ! Right(getCurrentState)
      } else
        sender() ! Left(s"Wallet with id $walletId is not found")

    // Handle wallet status change
    case WalletStatusCmd(_, active) =>
      // On status change set amount to 0.0 to avoid balance change
      if (customerId != "") {
        performPersist(
          Wallet(persistenceId, customerId, name, 0.0, new Date,
            TransactionType.DEPOSIT, s"Status change to active $active", active),
          Some(sender()), None, true)
      } else {
        sender() ! Left(s"Wallet with id $walletId is not found")
      }

    // Handle transfer deposit command
    case WalletTransferDepositCmd(amt, fromWallet, fromWalletId) =>
      if (active && !customerId.isEmpty) {
        val wallet = Wallet(persistenceId, customerId, name, amt, new Date,
          TransactionType.TRANSFER_DEPOSIT, s"Transfer from $fromWalletId")
        log.info(s"Perform transfer deposit $amt wallet $persistenceId")
        // We need to keep this reference in case persist fails during transfer deposit
        // to notify wallet that sent money to perform rollback and restore funds
        fromWalletRef = Some(fromWallet)
        performPersist(wallet, fromWalletRef, Some(WalletTransferSuccessCmd(self, fromWalletId, persistenceId, amt)))
      } else {
        fromWallet ! WalletTransferFailedCmd(self, persistenceId, amt)
      }

    // Handle transfer withdraw command
    case WalletTransferWithdrawCmd(amt, toWallet, toWalletId) =>
      val op = checkFundsAvailable(amt, TransactionType.TRANSFER_WITHDRAW)
      if (op != 0 && active) {
        log.info(s"Perform transfer withdraw $amt wallet $persistenceId")
        val wallet = Wallet(persistenceId, customerId, name, amt * op, new Date,
          TransactionType.TRANSFER_WITHDRAW, s"Transfer to $toWalletId")
        performPersist(wallet, Some(toWallet), Some(WalletTransferDepositCmd(amt, self, persistenceId)))
        serviceActorRef = Some(sender())
        //sender() ! WalletTransferResult(persistenceId, toWalletId, amt, WalletNotifications.TRANSFER_OK)
      } else {
        // Transfer withdraw failed send message to parent and stop both wallets
        sender() ! WalletTransferResult(persistenceId, toWalletId, amt, "Transfer failed")
      }

    // Handle transfer success command
    case WalletTransferSuccessCmd(toWallet, fromWalletId, toWalletId, amt) =>
      log.info(s"Received transfer success from wallet $toWalletId amount $amt")
      serviceActorRef.get ! WalletTransferResult(persistenceId, toWalletId, amt, WalletNotifications.TRANSFER_OK)
      serviceActorRef = None

    // Handle transfer failed command
    case WalletTransferFailedCmd(toWallet, toWalletId, amt) =>
      // Add money back to the wallet in case transfer deposit failed
      log.info(s"Received transfer failed from wallet $toWalletId amount $amt")
      val wallet = Wallet(persistenceId, customerId, name, amt, new Date,
        TransactionType.DEPOSIT, s"Restore from failed transfer to $toWalletId")
      performPersist(wallet, serviceActorRef,
                Some(WalletTransferResult(persistenceId, toWalletId, amt, WalletNotifications.TRANSFER_FAILED)))
      serviceActorRef = None

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Snapshot saved successfully $metadata")

  }

  /**
   *
   * @return
   */
  override def receiveRecover: Receive = {

    case wallet @ Wallet(_, _, _, _, _, _, _, _) =>
      updateCurrentState(wallet)

    case SnapshotOffer(metadata, wallet) =>
      log.info(s"Recovered from snapshot wallet: $wallet")
      updateCurrentState(wallet.asInstanceOf[Wallet])
  }

  override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    val walletEvent = event.asInstanceOf[Wallet]
    if (walletEvent.transactionType == TransactionType.TRANSFER_DEPOSIT) {
      // Send transfer failed notification to fromWallet
      fromWalletRef match {
        case Some(actorRef) =>
          actorRef ! WalletTransferFailedCmd(self, walletEvent.id, walletEvent.amount)
          fromWalletRef = None
        case None => log.error(s"Failed to notify on transfer deposit failure")
      }
    }
    super.onPersistFailure(cause, event, seqNr)
  }

  private def performPersist(wallet: Wallet, senderRef: Option[ActorRef], msg: Option[Any], wrapMsg: Boolean = false): Unit = {
    persist(Tagged(wallet, Set("wallet"))) { t =>
      val w = t.payload.asInstanceOf[Wallet]
      updateCurrentState(w)
      saveSnapshot(getCurrentState)
      senderRef match {
        case Some(actorRef) =>
          if (wrapMsg)
            actorRef ! Right(msg.getOrElse(getCurrentState))
          else
            actorRef ! msg.getOrElse(getCurrentState)
        case None => ;
      }
    }
  }

  private def checkFundsAvailable(amt: Double, transactionType: String): Int = {
    transactionType match {
      case TransactionType.WITHDRAW => if (amount >= amt) -1 else 0
      case TransactionType.TRANSFER_WITHDRAW => if (amount >= amt) -1 else 0
      case _ => 1
    }
  }

  private def getCurrentState(): Wallet =
    Wallet(persistenceId, customerId, name, amount, timeStamp, transactionType, comment, active)

  private def updateCurrentState(wallet: Wallet): Unit = {

    customerId = wallet.customerId
    name = wallet.name
    amount += wallet.amount
    timeStamp = wallet.timestamp
    transactionType = wallet.transactionType
    comment = wallet.comment
    active = wallet.active
  }

}
