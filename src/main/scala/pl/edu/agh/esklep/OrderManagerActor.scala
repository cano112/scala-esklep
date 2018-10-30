package pl.edu.agh.esklep

import akka.actor.{ActorRef, LoggingFSM, Props}
import pl.edu.agh.esklep.CheckoutActor.Messages.{SelectDeliveryMethodCommand, SelectPaymentMethodCommand}
import pl.edu.agh.esklep.Model.{Delivery, Item, PaymentMethod}
import pl.edu.agh.esklep.OrderManagerActor.FSM._
import pl.edu.agh.esklep.OrderManagerActor.Messages._

class OrderManagerActor extends LoggingFSM[ManagerState, ManagerData] {
  startWith(Uninitialized, CartData(context.actorOf(Props[CartActor], "cartActor")))

  when(Uninitialized) {
    case Event(AddItemCommand(item), CartData(cart)) =>
      cart ! CartActor.Messages.AddItemCommand(item)
      sender ! Done
      goto(Open) using CartData(cart)
  }

  when(Open) {
    case Event(AddItemCommand(item), CartData(cartActor)) =>
      cartActor ! CartActor.Messages.AddItemCommand(item)
      sender ! Done
      stay
    case Event(RemoveItemCommand(item), CartData(cartActor)) =>
      cartActor ! CartActor.Messages.RemoveItemCommand(item)
      sender ! Done
      stay
    case Event(BuyCommand, CartData(cartActor)) =>
      cartActor ! CartActor.Messages.StartCheckoutCommand()
      stay using CartDataWithSender(cartActor, sender)
    case Event(StartCheckoutCommand(checkoutRef), CartDataWithSender(_, requestSender)) =>
      requestSender ! Done
      goto(InCheckout) using CheckoutData(checkoutRef)
  }

  when(InCheckout) {
    case Event(SelectDeliveryPaymentMethodCommand(delivery, paymentMethod), CheckoutData(checkoutRef)) =>
      checkoutRef ! SelectDeliveryMethodCommand(delivery)
      checkoutRef ! SelectPaymentMethodCommand(paymentMethod)
      stay using CheckoutDataWithSender(checkoutRef, sender)
    case Event(StartPaymentServiceCommand(paymentActor), CheckoutDataWithSender(_, requestSender)) =>
      requestSender ! Done
      goto(InPayment) using PaymentData(paymentActor)
  }

  when(InPayment) {
    case Event(PayCommand, PaymentData(paymentRef)) =>
      paymentRef ! PaymentActor.Messages.PayCommand
      stay using PaymentDataWithSender(paymentRef, sender)
    case Event(ConfirmPaymentCommand, PaymentDataWithSender(_, requestSender)) =>
      requestSender ! Done
      goto(Finished)
  }

  when(Finished) {
    case Event(ResetCommand, _) =>
      goto(Uninitialized) using Empty
  }
}

object OrderManagerActor {

  object FSM {
    sealed trait ManagerState
    case object Uninitialized extends ManagerState
    case object Open          extends ManagerState
    case object InCheckout    extends ManagerState
    case object InPayment     extends ManagerState
    case object Finished      extends ManagerState

    sealed trait ManagerData
    case object Empty                                                          extends ManagerData
    case class CartData(cartRef: ActorRef)                                     extends ManagerData
    case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef)         extends ManagerData
    case class CheckoutData(checkoutRef: ActorRef)                             extends ManagerData
    case class CheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends ManagerData
    case class PaymentData(paymentRef: ActorRef)                               extends ManagerData
    case class PaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef)   extends ManagerData
  }

  object Messages {
    sealed trait ManagerMessage

    sealed trait Ack
    case object Done extends Ack

    sealed trait ManagerCommand                                                               extends ManagerMessage
    case class AddItemCommand(item: Item)                                                     extends ManagerCommand
    case class RemoveItemCommand(item: Item)                                                  extends ManagerCommand
    case class SelectDeliveryPaymentMethodCommand(delivery: Delivery, payment: PaymentMethod) extends ManagerCommand
    case class StartPaymentServiceCommand(paymentActor: ActorRef)                             extends ManagerCommand
    case class StartCheckoutCommand(checkoutActor: ActorRef)                                  extends ManagerCommand
    case object BuyCommand                                                                    extends ManagerCommand
    case object PayCommand                                                                    extends ManagerCommand
    case object ConfirmPaymentCommand                                                         extends ManagerCommand
    case object ResetCommand                                                                  extends ManagerCommand
  }
}
