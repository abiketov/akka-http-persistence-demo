package org.demo.example.domain

import java.util.Date

import akka.actor.{ ActorRef, Props }
import akka.persistence.journal.Tagged
import akka.persistence.{ SaveSnapshotSuccess, SnapshotOffer }
import org.demo.example.domain.DomainModel._


object CustomerRepository {
  def props(id: String): Props = Props(new CustomerRepository(id))
}

class CustomerRepository(customerId: String) extends BaseRepository {

  private var firstName = ""
  private var lastName = ""
  private var email = ""
  private var timeStamp = new Date
  private var wallets = List.empty[String]
  private var active = true

  override def persistenceId: String = customerId



  override def receiveCommandBehavior: Receive = {
    // Create or Update Customer
    case cmd @ CustomerCmd(_, firstName, lastName, email) =>
      log.info(s"Received $cmd")
      if (active) {
        val customer = Customer(persistenceId, firstName, lastName, email, new Date, Nil)
        log.info(s"Customer created/updated $customer")
        performPersist(customer, Some(sender), None)
      } else {
        sender() ! Left(s"Customer with id $persistenceId is not active and can't be updated")
      }

    //Handle customer status update
    case CustomerStatusUpdateCmd(_, status) =>
      val customer = Customer(persistenceId, firstName, lastName, email, new Date, wallets, status)
      log.info(s"Customer  status is updated $customer")
      performPersist(customer, Some(sender), None)

    // Return Customer instance to the requester
    case CustomerGetCmd(custId) =>
      log.info(s"Load customer with id $custId")
      if (email != "")
        sender() ! Right(getCurrentState)
      else
        sender() ! Left(s"Customer with id $custId is not found")

    // Update Customer wallets
    case CustomerWalletAddCmd(_, walletId) =>
      if (active) {
        if (!wallets.contains(walletId)) {
          val updatedWallets = walletId :: wallets
          val customer = Customer(persistenceId, firstName, lastName, email, timeStamp, updatedWallets)
          log.info(s"Customer wallet added $customer")
          performPersist(customer, Some(sender), None)
        }
      } else {
        sender() ! Left(s"Customer with id $persistenceId is not active and can't be updated")
      }

    case CustomerWalletDelCmd(_, walletId) =>
      if (active) {
        val updatedWallets = wallets.filter(_ != walletId)
        val customer = Customer(persistenceId, firstName, lastName, email, timeStamp, updatedWallets)
        log.info(s"Customer wallet deleted $customer")
        performPersist(customer, Some(sender), None)
      } else {
        sender() ! Left(s"Customer with id $persistenceId is not active and can't be updated")
      }
    //Log successful snapshot update
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Snapshot updated successfully: $metadata")


  }
  override def receiveRecover: Receive = {

    case cust @ Customer(_, _, _, _, _, _, _) =>
      log.info(s"Recovering customer $cust")
      updateCurrentState(cust)

    case SnapshotOffer(metadata, cust) =>
      log.info(s"Recovered from snapshot $cust")
      updateCurrentState(cust.asInstanceOf[Customer])
  }

  private def performPersist(customer: Customer, senderRef: Option[ActorRef], msg: Option[Any]): Unit = {
    persist(Tagged(customer, Set("customer"))) { t =>
      val w = t.payload.asInstanceOf[Customer]
      updateCurrentState(w)
      saveSnapshot(getCurrentState)
      senderRef match {
        case Some(actorRef) => Right(actorRef ! msg.getOrElse(getCurrentState))
        case None => ;
      }
    }
  }

  private def getCurrentState(): Customer =
    Customer(persistenceId, firstName, lastName, email, timeStamp, wallets, active)

  private def updateCurrentState(customer: Customer): Unit = {
    wallets = customer.wallets
    firstName = customer.firstName
    lastName = customer.lastName
    email = customer.email
    timeStamp = customer.timestamp
    active = customer.active
  }

}
