package pl.edu.agh.esklep

import akka.actor.{ActorRef, LoggingFSM, PoisonPill}
import pl.edu.agh.esklep.CheckoutActor.Messages.ReceivePaymentCommand
import pl.edu.agh.esklep.OrderManagerActor.Messages.ConfirmPaymentCommand
import pl.edu.agh.esklep.PaymentActor.FSM._
import pl.edu.agh.esklep.PaymentActor.Messages.PayCommand

class PaymentActor extends LoggingFSM[PaymentActorState, PaymentActorData] {
  startWith(Idle, CheckoutData(sender))

  when(Idle) {
    case Event(PayCommand, CheckoutData(checkoutRef)) =>
      sender ! ConfirmPaymentCommand
      checkoutRef ! ReceivePaymentCommand()
      self ! PoisonPill
      stay
  }
}

object PaymentActor {
  object FSM {
    sealed trait PaymentActorState
    case object Idle extends PaymentActorState

    sealed trait PaymentActorData
    case class CheckoutData(actor: ActorRef) extends PaymentActorData
  }

  object Messages {
    sealed trait PaymentActorMessage

    sealed trait PaymentActorCommand extends PaymentActorMessage
    case object PayCommand           extends PaymentActorCommand
  }

}
