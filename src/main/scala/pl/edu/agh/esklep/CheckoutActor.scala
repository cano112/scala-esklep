package pl.edu.agh.esklep

import akka.actor.{ActorRef, LoggingFSM, PoisonPill, Props}
import pl.edu.agh.esklep.CheckoutActor.FSM._
import pl.edu.agh.esklep.CheckoutActor.Messages._
import pl.edu.agh.esklep.Model.{Delivery, PaymentMethod}

import scala.concurrent.duration._

class CheckoutActor extends LoggingFSM[CheckoutState, CheckoutData] {
  startWith(Idle, CartData(context.parent))

  when(Idle) {
    case Event(StartCheckoutCommand(orderManager: ActorRef), CartData(cartActor)) =>
      sender ! CheckoutStartedEvent()
      startCheckoutTimer()
      goto(SelectingDelivery) using OrderManagerAndCartActor(orderManager, cartActor)
  }

  when(SelectingDelivery) {
    case Event(CancelCheckoutCommand() | ExpireCheckoutCommand(), _) =>
      sender ! CheckoutCancelledEvent()
      goto(Cancelled)
    case Event(SelectDeliveryMethodCommand(delivery), OrderManagerAndCartActor(orderManager, cartActor)) =>
      sender ! DeliveryMethodSelectedEvent(delivery)
      goto(SelectingPaymentMethod) using DeliveryWithManagerAndCart(delivery, orderManager, cartActor)
  }

  when(Cancelled) {
    case Event(ExpireCheckoutCommand(), _) =>
      sender ! CheckoutExpiredEvent()
      self ! CancelCheckoutCommand()
      stay
    case Event(ExpirePaymentCommand(), _) =>
      sender ! PaymentExpiredEvent()
      self ! CancelCheckoutCommand()
      stay
    case Event(CancelCheckoutCommand(), OrderManagerAndCartActor(_, cartActor)) =>
      self ! PoisonPill
      cartActor ! CartActor.Messages.CancelCheckoutCommand()
      stay
  }

  when(SelectingPaymentMethod) {
    case Event(
        CancelCheckoutCommand() | ExpireCheckoutCommand(),
        DeliveryWithManagerAndCart(_, orderManager, cartActor)
        ) =>
      sender ! CheckoutCancelledEvent()
      goto(Cancelled) using OrderManagerAndCartActor(orderManager, cartActor)
    case Event(SelectPaymentMethodCommand(paymentMethod), DeliveryWithManagerAndCart(delivery, manager, cart)) =>
      val paymentActor = context.actorOf(Props[PaymentActor], "paymentActor")
      sender ! PaymentMethodSelectedEvent(paymentMethod)
      manager ! OrderManagerActor.Messages.StartPaymentServiceCommand(paymentActor)
      startPaymentTimer()
      goto(ProcessingPayment) using PaymentDeliveryWithManagerAndCart(delivery, paymentMethod, manager, cart)
  }

  when(ProcessingPayment) {
    case Event(CancelCheckoutCommand() | ExpirePaymentCommand(), _) =>
      sender ! CheckoutCancelledEvent()
      goto(Cancelled)
    case Event(ReceivePaymentCommand(), PaymentDeliveryWithManagerAndCart(_, _, _, cart)) =>
      sender ! PaymentReceivedEvent()
      cart ! CartActor.Messages.CloseCheckoutCommand()
      self ! PoisonPill
      stay
  }

  private def startCheckoutTimer(): Unit =
    setTimer("checkoutTimer", ExpireCheckoutCommand(), CheckoutActor.Timers.CHECKOUT_TIMER)

  private def startPaymentTimer(): Unit =
    setTimer("paymentTimer", ExpirePaymentCommand(), CheckoutActor.Timers.PAYMENT_TIMER)
}

object CheckoutActor {

  object Timers {
    val CHECKOUT_TIMER: FiniteDuration = 5 seconds
    val PAYMENT_TIMER: FiniteDuration  = 5 seconds
  }

  object FSM {

    sealed trait CheckoutState
    case object Idle                   extends CheckoutState
    case object SelectingDelivery      extends CheckoutState
    case object SelectingPaymentMethod extends CheckoutState
    case object ProcessingPayment      extends CheckoutState
    case object Cancelled              extends CheckoutState

    sealed trait CheckoutData
    case object Nothing                                                                          extends CheckoutData
    case class CartData(actor: ActorRef)                                                         extends CheckoutData
    case class OrderManagerAndCartActor(orderManager: ActorRef, cartActor: ActorRef)             extends CheckoutData
    case class DeliveryWithManagerAndCart(delivery: Delivery, manager: ActorRef, cart: ActorRef) extends CheckoutData
    case class PaymentDeliveryWithManagerAndCart(
      delivery: Delivery,
      payment: PaymentMethod,
      manager: ActorRef,
      cart: ActorRef
    ) extends CheckoutData
  }

  object Messages {

    sealed trait CheckoutMessage

    sealed trait CheckoutCommand                                        extends CheckoutMessage
    case class StartCheckoutCommand(orderManagerRef: ActorRef)          extends CheckoutCommand
    case class CancelCheckoutCommand()                                  extends CheckoutCommand
    case class SelectDeliveryMethodCommand(delivery: Delivery)          extends CheckoutCommand
    case class SelectPaymentMethodCommand(paymentMethod: PaymentMethod) extends CheckoutCommand
    case class ReceivePaymentCommand()                                  extends CheckoutCommand
    case class ExpireCheckoutCommand()                                  extends CheckoutCommand
    case class ExpirePaymentCommand()                                   extends CheckoutCommand

    sealed trait CheckoutEvent                                          extends CheckoutMessage
    case class CheckoutStartedEvent()                                   extends CheckoutEvent
    case class CheckoutCancelledEvent()                                 extends CheckoutEvent
    case class DeliveryMethodSelectedEvent(delivery: Delivery)          extends CheckoutEvent
    case class PaymentMethodSelectedEvent(paymentMethod: PaymentMethod) extends CheckoutEvent
    case class PaymentReceivedEvent()                                   extends CheckoutEvent
    case class CheckoutExpiredEvent()                                   extends CheckoutEvent
    case class PaymentExpiredEvent()                                    extends CheckoutEvent
  }

}
