package org.demo.example.utils

import java.text.SimpleDateFormat
import java.util.Date

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.demo.example.domain.DomainModel._
import spray.json.{ DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat }

object DateParser {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  implicit object DateFormat extends JsonFormat[Date] {

    def write(date: Date) = JsString(dateToIsoString(date))

    def read(json: JsValue) = json match {
      case JsString(rawDate) => parseIsoDateString(rawDate)
      case _ => new Date()
    }
  }

  private def dateToIsoString(date: Date) =
    dateFormat.format(date)

  private def parseIsoDateString(date: String): Date =
    dateFormat.parse(date)

  implicit def rootEitherFormat[A: RootJsonFormat, B: RootJsonFormat] = new RootJsonFormat[Either[A, B]] {
    val format = DefaultJsonProtocol.eitherFormat[A, B]

    def write(either: Either[A, B]) = format.write(either)

    def read(value: JsValue) = format.read(value)
  }

}

trait JsonSupport extends SprayJsonSupport {

  import DateParser._
  import DefaultJsonProtocol._

  implicit val walletJsonFormat = jsonFormat8(Wallet)
  implicit val transactions = jsonFormat1(Transactions)
  implicit val customerJsonFormat = jsonFormat7(Customer)
  implicit val responseError = jsonFormat1(ResponseError)
  implicit val customerCmd = jsonFormat4(CustomerCmd)
  implicit val updateCustomer = jsonFormat1(UpdateCustomer)
  implicit val walletCmd = jsonFormat6(WalletCmd)
  implicit val walletUpdate = jsonFormat1(WalletUpdate)
  implicit val walletTransactions = jsonFormat1(WalletTransactions)
  implicit val walletBalanceCmd = jsonFormat3(WalletBalanceUpdateCmd)
  implicit val walletBalance = jsonFormat1(WalletBalanceUpdate)
  implicit val walletTransfer = jsonFormat3(WalletTransfer)
  implicit val walletTransferResult = jsonFormat4(WalletTransferResult)
  implicit val walletStatusCmd = jsonFormat2(WalletStatusCmd)
  implicit val walletStatusUpdate = jsonFormat1(WalletStatusUpdate)
  implicit val customerStatusCmd = jsonFormat2(CustomerStatusUpdateCmd)
  implicit val customerStatusUpdate = jsonFormat1(UpdateCustomerStatus)
  implicit val customers = jsonFormat1(Customers)
  implicit val wallets = jsonFormat1(Wallets)

}

