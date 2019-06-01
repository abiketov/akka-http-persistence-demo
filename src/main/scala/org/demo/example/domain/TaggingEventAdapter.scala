package org.demo.example.domain

import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import org.demo.example.domain.DomainModel.{ Customer, Wallet }

class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = "eventAdapter"

  override def toJournal(event: Any): Any = event match {
    case event @ Customer(_, _, _, _, _, _, _) =>
      Tagged(event, Set("customer"))
    case event @ Wallet(_, _, _, _, _, _, _, _) =>
      Tagged(event, Set("wallet"))
    case event => event
  }
}

