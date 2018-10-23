package pl.edu.agh.esklep

import akka.actor.{FSM, PoisonPill}
import pl.edu.agh.esklep.Messages.Cart._
import pl.edu.agh.esklep.Messages.Checkout
import pl.edu.agh.esklep.Model.Cart.{Empty, InCheckout, Items, NonEmpty}
import pl.edu.agh.esklep.Model.Checkout._
import pl.edu.agh.esklep.Model.{Data, State}

import scala.concurrent.duration._

object ActorsFSM {
  object Timers {
    val CART_TIMER: FiniteDuration = 5 seconds
    val CHECKOUT_TIMER: FiniteDuration = 5 seconds
    val PAYMENT_TIMER: FiniteDuration = 5 seconds
  }

  class CartActor extends FSM[State, Data] {
    startWith(Empty, Items(Nil))

    when(Empty) {
      case Event(AddItemCommand(i), _) =>
        val items = i :: Nil
        context.parent ! ItemAddedEvent(items, i)
        goto(NonEmpty) using Items(items)
    }

    when(NonEmpty, stateTimeout = Timers.CART_TIMER) {
      case Event(StartCheckoutCommand(), Items(items)) =>
        context.parent ! CheckoutStartedEvent(items)
        goto(InCheckout) using Items(items)
      case Event(AddItemCommand(i), Items(items)) =>
        val newItems = items :+ i
        context.parent ! ItemAddedEvent(newItems, i)
        goto(NonEmpty) using Items(newItems)
      case Event(RemoveItemCommand(i), Items(items)) =>
        if (items.size == 1 && items.contains(i)) {
          context.parent ! ItemRemovedEvent(Nil, i)
          goto(Empty) using Items(Nil)
        }
        else {
          val newItems = items.filter(item => item != i)
          context.parent ! ItemRemovedEvent(newItems, i)
          goto(NonEmpty) using Items(newItems)
        }
      case Event(StateTimeout, _) =>
        self ! ExpireCartCommand()
        stay
      case Event(ExpireCartCommand(), _) =>
        context.parent ! TimerExpiredEvent()
        goto(Empty) using Items(Nil)
    }

    when(InCheckout) {
      case Event(CloseCheckoutCommand(), Items(items)) =>
        context.parent ! CheckoutClosedEvent(items)
        goto(Empty) using Items(Nil)
      case Event(CancelCheckoutCommand(), Items(items)) =>
        context.parent ! CheckoutCancelledEvent(items)
        goto(NonEmpty) using Items(items)
    }
  }

  class CheckoutActor extends FSM[State, Data] {
    startWith(Idle, Nothing)

    when(Idle) {
      case Event(Checkout.StartCheckoutCommand(), _) =>
        context.parent ! Checkout.CheckoutStartedEvent()
        startCheckoutTimer()
        goto(SelectingDelivery)
    }

    when(SelectingDelivery) {
      case Event(Checkout.CancelCheckoutCommand() | Checkout.ExpireCheckoutCommand(), _) =>
        context.parent ! Checkout.CheckoutCancelledEvent()
        goto(Cancelled)
      case Event(Checkout.SelectDeliveryMethodCommand(), _) =>
        context.parent ! Checkout.DeliveryMethodSelectedEvent()
        goto(SelectingPaymentMethod)
    }

    when(Cancelled) {
      case Event(Checkout.ExpireCheckoutCommand(), _) =>
        context.parent ! Checkout.CheckoutExpiredEvent()
        self ! Checkout.CancelCheckoutCommand()
        stay
      case Event(Checkout.ExpirePaymentCommand(), _) =>
        context.parent ! Checkout.PaymentExpiredEvent()
        self ! Checkout.CancelCheckoutCommand()
        stay
      case Event(Checkout.CancelCheckoutCommand(), _) =>
        self ! PoisonPill
        stay
    }

    when(SelectingPaymentMethod) {
      case Event(Checkout.CancelCheckoutCommand() | Checkout.ExpireCheckoutCommand(), _) =>
        context.parent ! Checkout.CheckoutCancelledEvent()
        goto(Cancelled)
      case Event(Checkout.SelectPaymentMethodCommand(), _) =>
        context.parent ! Checkout.PaymentMethodSelectedEvent()
        startPaymentTimer()
        goto(ProcessingPayment)
    }

    when(ProcessingPayment) {
      case Event(Checkout.CancelCheckoutCommand() | Checkout.ExpirePaymentCommand(), _) =>
        context.parent ! Checkout.CheckoutCancelledEvent()
        goto(Cancelled)
      case Event(Checkout.ReceivePaymentCommand(), _) =>
        context.parent ! Checkout.PaymentReceivedEvent()
        goto(Closed)
    }

    when(Closed) {
      case Event(_, _) =>
        self ! PoisonPill
        stay
    }

    private def startCheckoutTimer(): Unit =
      setTimer("checkoutTimer", Checkout.ExpireCheckoutCommand(), Timers.CHECKOUT_TIMER)

    private def startPaymentTimer(): Unit =
      setTimer("paymentTimer", Checkout.ExpirePaymentCommand(), Timers.PAYMENT_TIMER)
  }
}
