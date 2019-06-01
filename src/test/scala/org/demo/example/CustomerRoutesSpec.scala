package org.demo.example

import java.util.Date

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import akka.util.Timeout
import org.demo.example.controller.CustomerRoutes
import org.demo.example.domain.DomainModel._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.duration._
import scala.reflect.io.Path

class CustomerRoutesSpec extends WordSpec with Matchers
  with ScalaFutures with ScalatestRouteTest
  with BeforeAndAfterAll
  with CustomerRoutes {

  implicit val timeout = Timeout(5 seconds)
  val testCustomerService = TestProbe("customerService")
  val customerService = testCustomerService.ref

  lazy val routes = customerRoutes

  override def afterAll(): Unit = {

    val path = Path("testStore")
    path.deleteRecursively()
  }

  "CustomerRoutes" should {

    "return 1 customer (GET /api/customers/c01)" in {

      val customer = Customer("c01", "John", "Smith", "john.smith@aol.com", new Date, Nil)

      val request = HttpRequest(uri = "/api/customers/c01")

      val result = request ~> routes ~> runRoute

      testCustomerService.expectMsg(GetCustomer(CustomerGetCmd("c01")))
      testCustomerService.reply(Right(customer))

      check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[Customer].id should ===("c01")
      }(result)
    }

    "return customer Not found (GET /api/customers/c02)" in {

      val customer = Customer("c01", "John", "Smith", "john.smith@aol.com", new Date, Nil)

      val request = HttpRequest(uri = "/api/customers/c02")

      val result = request ~> routes ~> runRoute

      testCustomerService.expectMsg(GetCustomer(CustomerGetCmd("c02")))
      testCustomerService.reply(Left("Customer with id c02 is not found"))

      check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("Customer with id c02 is not found")
      }(result)
    }

    "be able to create new Customer (POST /api/customers)" in {
      val customer = Customer("c01", "John", "Smith", "john.smith@aol.com", new Date, Nil)
      val customerCmd = UpdateCustomer(CustomerCmd(Some("c01"), "John", "Smith", "john.smith@aol.com"))
      val entity = Marshal(customerCmd).to[MessageEntity].futureValue

      val request = Post("/api/customers").withEntity(entity)

      val result = request ~> routes ~> runRoute

      testCustomerService.expectMsg(customerCmd)
      testCustomerService.reply(customer)

      check {
        status should ===(StatusCodes.Created)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[Customer].id should ===("c01")
        entityAs[Customer].firstName should ===("John")
        entityAs[Customer].email should ===("john.smith@aol.com")
      }(result)
    }

    "be able to update Customer (PUT /api/customers)" in {
      val customer = Customer("c01", "John", "Smith", "john.smith@gmail.com", new Date, Nil)
      val customerCmd = UpdateCustomer(CustomerCmd(Some("c01"), "John", "Smith", "john.smith@gmail.com"))
      val entity = Marshal(customerCmd).to[MessageEntity].futureValue

      val request = Put("/api/customers").withEntity(entity)

      val result = request ~> routes ~> runRoute

      testCustomerService.expectMsg(customerCmd)
      testCustomerService.reply(customer)

      check {
        status should ===(StatusCodes.Accepted)
        contentType should ===(ContentTypes.`application/json`)

        entityAs[Customer].id should ===("c01")
        entityAs[Customer].firstName should ===("John")
        entityAs[Customer].email should ===("john.smith@gmail.com")
      }(result)
    }
  }
}
