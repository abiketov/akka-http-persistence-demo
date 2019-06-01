package org.demo.example.controller

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.util.Timeout
import org.demo.example.domain.DomainModel._
import org.demo.example.utils.JsonSupport
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CustomerRoutes extends JsonSupport {

  implicit def timeout: Timeout
  implicit def system: ActorSystem
  def customerService: ActorRef

  lazy val customerRoutes: Route =
    pathPrefix("api") {
      get {
        path("customers") {
          val resultFuture = (customerService ? AllCustomers).mapTo[Seq[Customer]]
          val entityFuture = resultFuture.map { s =>
            val custMap = Map(s map { a => a.id -> a }: _*)
            HttpEntity(
              ContentTypes.`application/json`,
              Customers(custMap.map { case (_, v) => v }.toSeq).toJson.compactPrint)
          }
          complete(entityFuture)
        } ~
          path("customers" / Segment) { customerId =>
            val resp: Future[Either[String, Customer]] =
              (customerService ? GetCustomer(CustomerGetCmd(customerId))).mapTo[Either[String, Customer]]
            val entity = resp.map { value =>
              val body = value match {
                case Left(s) => s
                case Right(c) => c.toJson.prettyPrint
              }
              HttpEntity(ContentTypes.`application/json`, body)
            }
            complete(StatusCodes.OK, entity)
          }
      } ~
        post {
          // Create new Customer
          path("customers") {
            entity(implicitly[FromRequestUnmarshaller[UpdateCustomer]]) { customerCmd =>
              val customer = (customerService ? customerCmd).mapTo[Customer]
              complete((StatusCodes.Created, customer))
            }
          }
        } ~
        put {
          // Update Customer
          path("customers") {
            entity(implicitly[FromRequestUnmarshaller[UpdateCustomer]]) { customerCmd =>
              val customer = (customerService ? customerCmd).mapTo[Customer]
              complete((StatusCodes.Accepted, customer))
            }
          } ~
            path("customers" / "status") {
              entity(implicitly[FromRequestUnmarshaller[UpdateCustomerStatus]]) { customerCmd =>
                val customer = (customerService ? customerCmd).mapTo[Customer]
                complete(customer)
              }
            }
        }
    }

}
