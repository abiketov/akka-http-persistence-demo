package org.demo.example.service

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.pattern.{ask, pipe}
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import org.demo.example.domain.DomainModel.{BaseCommandWithId, _}
import org.demo.example.domain.{CustomerRepository, WalletRepository}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait SystemService extends Actor with ActorLogging {

  implicit val materializer = ActorMaterializer()(context.system)
  implicit val timeout = Timeout(5 seconds)
  implicit val executionContext: ExecutionContext = context.dispatcher

  def generateId(): String = java.util.UUID.randomUUID.toString

  val queries = PersistenceQuery(context.system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

  def getAllEntities(tag: String): Future[immutable.Seq[Any]] = {
    val src: Source[EventEnvelope, NotUsed] = queries.currentEventsByTag(tag, offset = Sequence(0L))
    val events: Source[Any, NotUsed] = src.map(_.event)
    events.runWith(Sink.seq)
  }

  def repoActors: scala.collection.mutable.Map[String,ActorRef]

  def getRepositoryActor(name: String): ActorRef = {
    repoActors.get(self.path.toString+s"/${name}") match {
      case None =>
        val ref = context.actorOf(repositoryBuilder(name), name)
        repoActors += (ref.path.toString -> ref)
        context.watch(ref)
        ref
      case Some(ref) => ref
    }
  }

  def repositoryBuilder(name: String): Props

  override def receive:Receive =  receiveTerminated orElse businessReceive

  def businessReceive:Receive

  def receiveTerminated:Receive = {

    case Terminated(ref) =>
      repoActors -= ref.path.toString
      log.info(s"Remove from actor map ${ref.path}, map size is ${repoActors.size}")

  }
}

class CustomerServiceActor() extends SystemService {

  override val repoActors = scala.collection.mutable.Map.empty[String,ActorRef]

  override def repositoryBuilder(name: String):Props = CustomerRepository.props(name)

  override def businessReceive: Receive = {

    case msg: BaseMessage => msg.cmd match {
      case cmd: BaseCommand =>
        val currentId = cmd.id.getOrElse(generateId)
        val customerRepo = getRepositoryActor(currentId)
        (customerRepo ? cmd).pipeTo(sender())
        log.info(s"Sent stop message to Actor ${customerRepo.path}")

      case cmd @ _ => log.error(s"Unknown command $cmd")
    }

    case msg: BaseMessageWithId => msg.cmd match {
      case cmd: BaseCommandWithId =>
        val customerRepo = getRepositoryActor(cmd.id)
        (customerRepo ? cmd).pipeTo(sender())
        log.info(s"Sent stop message to Actor ${customerRepo.path}")

      case cmd @ _ => log.error(s"Unknown command $cmd")
    }

    case cust @ Customer(_, _, _, _, _, _, _) =>
      log.info(s"Customer successfully loaded $cust")

    case AllCustomers => getAllEntities("customer").pipeTo(sender())

    case msg @ _ => log.error(s"Unknown message $msg")
  }
}

object WalletServiceActor {
  def props(customerService: ActorRef) = Props(new WalletServiceActor(customerService))
}

class WalletServiceActor(customerService: ActorRef) extends Actor with SystemService with ActorLogging {
  override val repoActors = scala.collection.mutable.Map.empty[String,ActorRef]

  override def repositoryBuilder(name: String):Props = WalletRepository.props(name)

  override def businessReceive: Receive = {

    case msg: BaseMessage => msg.cmd match {
      case cmd: BaseCommand =>
        val currentId: String = cmd.id.getOrElse(generateId)
        val walletRepo = getRepositoryActor(currentId)
        (walletRepo ? cmd).pipeTo(sender())
        // Send message to customer service to add wallet to the customer
        // At this point we don't check if customer exists, but we should
        // in real system
        val walletCmd = cmd.asInstanceOf[WalletCmd]
        val customerId = walletCmd.customerId
        customerService ! AddCustomerWallet(CustomerWalletAddCmd(customerId, currentId))
        log.info(s"Sent stop message to Actor ${walletRepo.path}")

      case cmd @ _ => log.error(s"Unknown command $cmd")
    }

    case msg: BaseMessageWithId => msg.cmd match {
      case cmd: BaseCommandWithId =>
        val walletRepo = getRepositoryActor(cmd.id)
        implicit val timeout = Timeout(50 seconds)
        (walletRepo ? cmd).pipeTo(sender())
        log.info(s"Sent stop message to Actor ${walletRepo.path}")

      case cmd @ _ => log.error(s"Unknown command $cmd")
    }

    case WalletTransactions(walletId) =>
      val src: Source[EventEnvelope, NotUsed] =
        queries.currentEventsByPersistenceId(walletId, 0L, Long.MaxValue)
      val events = src.map(_.event)
      val trxs: Future[immutable.Seq[Any]] = events.runWith(Sink.seq)
      trxs.pipeTo(sender())

    case AllWallets => getAllEntities("wallet").pipeTo(sender())

    case wallet @ Wallet(_, _, _, _, _, _, _, _) =>
      log.info(s"Wallet successfully loaded $wallet")

    case WalletTransfer(fromWalletId, toWalletId, amount) =>
      val walletFrom = getRepositoryActor(fromWalletId)
      val walletTo = getRepositoryActor(toWalletId)
      (walletFrom ? WalletTransferWithdrawCmd(amount, walletTo, toWalletId)).pipeTo(sender())

    case WalletTransferResult(fromWalletId, toWalletId, amt, msg) =>
      log.info(msg + s" From wallet: $fromWalletId to wallet $toWalletId for amount $amt")

    case WalletError(msg) =>
      log.error(msg)

    case msg @ _ => log.error(s"Unknown message $msg")
  }
}

