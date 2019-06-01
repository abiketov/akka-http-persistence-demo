package org.demo.example

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import org.demo.example.domain.CustomerRepository
import org.demo.example.domain.DomainModel._
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import scala.reflect.io.Path

class CustomerRepositorySpec extends TestKit(ActorSystem("CustomerSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {

    TestKit.shutdownActorSystem(system)
    val path = Path("testStore")
    path.deleteRecursively()
  }

  "CustomerRepository" should {

    "create new customer" in {
      val customerId = "cust_0001"
      val customerCmd = CustomerCmd(Some(customerId), "John", "Smith", "john.smith@aol.com")
      val customerRepo = system.actorOf(CustomerRepository.props(customerId), customerId)
      customerRepo ! customerCmd

      val customer = expectMsgType[Customer]
      assert(customer.id === customerId)
      assert(customer.firstName === "John")
      assert(customer.lastName === "Smith")
      assert(customer.email === "john.smith@aol.com")
      assert(customer.wallets === Nil)
      Thread.sleep(150)

    }

    "update customer email" in {
      val customerId = "cust_0001"
      val customerRepo = system.actorOf(CustomerRepository.props(customerId), customerId)
      val customerCmd = CustomerCmd(Some(customerId), "John", "Smith", "john.smith@gmail.com")
      customerRepo ! customerCmd

      val customer = expectMsgType[Customer]
      assert(customer.id === customerId)
      assert(customer.firstName === "John")
      assert(customer.lastName === "Smith")
      assert(customer.email === "john.smith@gmail.com")
      assert(customer.wallets === Nil)
      Thread.sleep(150)

    }

    "get customer by id" in {
      val customerId = "cust_0001"
      val customerRepo = system.actorOf(CustomerRepository.props(customerId), customerId)
      val customerCmd = CustomerGetCmd(customerId)
      customerRepo ! customerCmd

      val response = expectMsgType[Either[String, Customer]]
      val customer = response.right.get
      assert(customer.id === customerId)
      assert(customer.firstName === "John")
      assert(customer.lastName === "Smith")
      assert(customer.email === "john.smith@gmail.com")
      assert(customer.wallets === Nil)
      Thread.sleep(150)

    }

    "add wallet to customer" in {
      val customerId = "cust_0009"
      val customerRepo = system.actorOf(CustomerRepository.props(customerId), customerId)
      val customerCmd = CustomerCmd(Some(customerId), "John", "Smith", "john.smith@aol.com")
      customerRepo ! customerCmd
      val c = expectMsgType[Customer]

      val addWalletCmd = CustomerWalletAddCmd(customerId, "wallet1")
      customerRepo ! addWalletCmd

      val customer = expectMsgType[Customer]
      assert(customer.id === customerId)
      assert(customer.firstName === "John")
      assert(customer.lastName === "Smith")
      assert(customer.email === "john.smith@aol.com")
      assert(customer.wallets === List("wallet1"))
      Thread.sleep(150)

    }

    "delete wallet to customer" in {
      val customerId = "cust_00010"
      val customerRepo = system.actorOf(CustomerRepository.props(customerId), customerId)
      val customerCmd = CustomerCmd(Some(customerId), "John", "Smith", "john.smith@aol.com")
      customerRepo ! customerCmd
      val c = expectMsgType[Customer]

      val addWalletCmd1 = CustomerWalletAddCmd(customerId, "wallet1")
      customerRepo ! addWalletCmd1
      expectMsgType[Customer]

      val addWalletCmd2 = CustomerWalletAddCmd(customerId, "wallet2")
      customerRepo ! addWalletCmd2
      expectMsgType[Customer]

      val delWalletCmd1 = CustomerWalletDelCmd(customerId, "wallet1")
      customerRepo ! delWalletCmd1

      val customer = expectMsgType[Customer]
      assert(customer.id === customerId)
      assert(customer.firstName === "John")
      assert(customer.lastName === "Smith")
      assert(customer.email === "john.smith@aol.com")
      assert(customer.wallets === List("wallet2"))
      Thread.sleep(150)

    }
  }
}
