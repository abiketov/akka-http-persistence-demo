package org.demo.example.domain

import java.util.Date

import akka.actor.ActorRef

object DomainModel {

  trait DomainEntity

  case class Customer(id: String, firstName: String, lastName: String,
    email: String, timestamp: Date, wallets: List[String], active: Boolean = true) extends DomainEntity

  case class Wallet(id: String, customerId: String, name: String, amount: Double,
    timestamp: Date, transactionType: String, comment: String, active: Boolean = true) extends DomainEntity

  case class Transactions(transactions: Seq[Wallet])
  case class ResponseError(msg: String)
  case class Customers(customers: Seq[Customer])
  case class Wallets(wallets: Seq[Wallet])

  object TransactionType {
    val DEPOSIT = "D"
    val WITHDRAW = "W"
    val TRANSFER_DEPOSIT = "TD"
    val TRANSFER_WITHDRAW = "TW"
  }

  trait BaseCommand {
    def id: Option[String]
  }

  trait BaseCommandWithId {
    def id: String
  }

  // Customer commands
  case class CustomerCmd(id: Option[String], firstName: String, lastName: String, email: String) extends BaseCommand {
    id match {
      case None => ;
      case Some(v) => require(!v.isEmpty, "Customer Id must be provided")
    }
    require(!firstName.isEmpty, "First name must be provided")
    require(!lastName.isEmpty, "Last name must be provided")
    require(!email.isEmpty, "Email name must be provided")
  }

  case class CustomerGetCmd(id: String) extends BaseCommandWithId {
    require(!id.isEmpty, "Customer Id must be provided")
  }
  case class CustomerWalletAddCmd(id: String, walletId: String) extends BaseCommandWithId {
    require(!id.isEmpty, "Customer Id must be provided")
    require(!walletId.isEmpty, "Wallet Id must be provided")
  }
  case class CustomerWalletDelCmd(id: String, walletId: String) extends BaseCommandWithId {
    require(!id.isEmpty, "Customer Id must be provided")
    require(!walletId.isEmpty, "Wallet Id must be provided")
  }

  case class CustomerStatusUpdateCmd(id: String, active: Boolean) extends BaseCommandWithId {
    require(!id.isEmpty, "Customer Id must be provided")
  }

  //Wallet commands
  case class WalletCmd(id: Option[String], customerId: String, name: String, amount: Double,
    transactionType: String, comment: String) extends BaseCommand {
    id match {
      case None => ;
      case Some(v) => require(!v.isEmpty, "Wallet Id must be provided")
    }
    require(!customerId.isEmpty, "Customer Id must be provided")
    require(amount >= 0, "Wallet amount can not be negative")
    require(
      !transactionType.isEmpty &&
        (transactionType == TransactionType.DEPOSIT ||
          transactionType == TransactionType.WITHDRAW),
      "Transaction type is not valid, supported (D,W)")
  }

  case class WalletBalanceUpdateCmd(id: String, amount: Double, transactionType: String) extends BaseCommandWithId {
    require(!id.isEmpty, "Wallet Id must be provided")
    require(amount >= 0, "Wallet amount can not be negative")
    require(
      !transactionType.isEmpty &&
        (transactionType == TransactionType.DEPOSIT ||
          transactionType == TransactionType.WITHDRAW),
      "Transaction type is not valid, supported (D,W)")
  }

  case class WalletGetCmd(id: String) extends BaseCommandWithId {
    require(!id.isEmpty, "Wallet Id must be provided")

  }
  case class WalletStatusCmd(id: String, active: Boolean) extends BaseCommandWithId {
    require(!id.isEmpty, "Wallet Id must be provided")
  }

  case object AllCustomers

  //Internal wallet commands
  case class WalletTransferWithdrawCmd(amount: Double, toWallet: ActorRef, toWalletId: String)
  case class WalletTransferDepositCmd(amount: Double, fromWallet: ActorRef, fromWalletId: String)
  case class WalletTransferFailedCmd(walletTo: ActorRef, toWalletId: String, amount: Double)
  case class WalletTransferSuccessCmd(walletTo: ActorRef, fromWallet: String, toWallet: String, amount: Double)

  trait BaseMessage {
    def cmd: BaseCommand
  }

  trait BaseMessageWithId {
    def cmd: BaseCommandWithId
  }

  // Customer messages for CustomerService
  case class UpdateCustomer(cmd: CustomerCmd) extends BaseMessage

  case class GetCustomer(cmd: CustomerGetCmd) extends BaseMessageWithId
  case class UpdateCustomerStatus(cmd: CustomerStatusUpdateCmd) extends BaseMessageWithId
  case class AddCustomerWallet(cmd: CustomerWalletAddCmd) extends BaseMessageWithId
  case class DelCustomerWallet(cmd: CustomerWalletDelCmd) extends BaseMessageWithId

  //Wallet messages for WalletService
  case class WalletUpdate(cmd: WalletCmd) extends BaseMessage

  case class WalletBalanceUpdate(cmd: WalletBalanceUpdateCmd) extends BaseMessageWithId
  case class WalletStatusUpdate(cmd: WalletStatusCmd) extends BaseMessageWithId
  case class WalletGet(cmd: WalletGetCmd) extends BaseMessageWithId

  case class WalletTransfer(fromWallet: String, toWallet: String, amount: Double)
  case class WalletError(msg: String)
  case class WalletTransactions(id: String)
  case class WalletTransferResult(fromWallet: String, toWallet: String, amount: Double, msg: String)
  case object AllWallets

  case object ActorShutdown

  //REST API Response
  object WalletNotifications {
    val INSUFFICIENT_FUNDS = "Insufficient funds in the wallet or wallet is not active"
    val TRANSFER_OK = "Transfer completed successfully"
    val TRANSFER_FAILED = "Transfer failed"
  }
}
