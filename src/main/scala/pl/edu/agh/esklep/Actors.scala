package pl.edu.agh.esklep

import akka.actor.{Actor, PoisonPill, Timers}
import pl.edu.agh.esklep.Messages.Cart._
import pl.edu.agh.esklep.Messages.Checkout
import pl.edu.agh.esklep.Model.Item

import scala.concurrent.duration._

object Actors {
  object Timers {
    val CART_TIMER: FiniteDuration = 5 seconds
    val CHECKOUT_TIMER: FiniteDuration = 5 seconds
    val PAYMENT_TIMER: FiniteDuration = 5 seconds
  }

  class CartActor extends Actor with Timers {

    override def receive: Receive = empty

    def empty: Receive = {
      case c: AddItemCommand =>
        val items = c.item :: Nil
        context become nonEmpty(items)
        context.parent ! ItemAddedEvent(items, c.item)
    }

    def nonEmpty(items: Seq[Item]): Receive = {
      case _: StartCheckoutCommand =>
        context become inCheckout(items)
        startCartTimer()
        context.parent ! CheckoutStartedEvent(items)
      case c: AddItemCommand =>
        val newItems = items :+ c.item
        context become nonEmpty(newItems)
        startCartTimer()
        context.parent ! ItemAddedEvent(newItems, c.item)
      case c: RemoveItemCommand =>
        if (items.size == 1 && items.contains(c.item)) {
          context become empty
          context.parent ! ItemRemovedEvent(Nil, c.item)
        }
        else {
          val newItems = items.filter(i => i != c.item)
          context become nonEmpty(newItems)
          context.parent ! ItemRemovedEvent(newItems, c.item)
        }
        startCartTimer()
      case _: ExpireCartCommand =>
        context become empty
        context.parent ! TimerExpiredEvent()
    }

    def inCheckout(items: Seq[Item]): Receive = {
      case _: CloseCheckoutCommand =>
        context become empty
        context.parent ! CheckoutClosedEvent(items)
      case _: CancelCheckoutCommand =>
        context become nonEmpty(items)
        context.parent ! CheckoutCancelledEvent(items)
    }

    private def startCartTimer(): Unit = timers.startSingleTimer("cartTimer", ExpireCartCommand(), Timers.CART_TIMER)
  }

  class CheckoutActor extends Actor with Timers {
    override def receive: Receive = {
      case _: Checkout.StartCheckoutCommand =>
        context become selectingDelivery
        startCheckoutTimer()
        context.parent ! Checkout.CheckoutStartedEvent()
    }

    def selectingDelivery: Receive = {
      case _: Checkout.CancelCheckoutCommand | _: Checkout.ExpireCheckoutCommand =>
        context become cancelled
        context.parent ! Checkout.CheckoutCancelledEvent()
      case _: Checkout.SelectDeliveryMethodCommand =>
        context become selectingPaymentMethod
        context.parent ! Checkout.DeliveryMethodSelectedEvent()
    }

    def cancelled: Receive = {
      case _: Checkout.ExpireCheckoutCommand =>
        context.parent ! Checkout.CheckoutExpiredEvent()
        self ! Checkout.CancelCheckoutCommand()
      case _: Checkout.ExpirePaymentCommand =>
        context.parent ! Checkout.PaymentExpiredEvent()
        self ! Checkout.CancelCheckoutCommand()
      case _: Checkout.CancelCheckoutCommand => self ! PoisonPill
    }

    def selectingPaymentMethod: Receive = {
      case _: Checkout.CancelCheckoutCommand | _: Checkout.ExpireCheckoutCommand =>
        context become cancelled
        context.parent ! Checkout.CheckoutCancelledEvent()
      case _: Checkout.SelectPaymentMethodCommand =>
        context become processingPayment
        startPaymentTimer()
        context.parent ! Checkout.PaymentMethodSelectedEvent()
    }

    def processingPayment: Receive = {
      case _: Checkout.CancelCheckoutCommand | _: Checkout.ExpirePaymentCommand =>
        context become cancelled
        context.parent ! Checkout.CheckoutCancelledEvent()
      case _: Checkout.ReceivePaymentCommand =>
        context become closed
        context.parent ! Checkout.PaymentReceivedEvent()
    }

    def closed: Receive = {
      case _ => self ! PoisonPill
    }

    private def startCheckoutTimer(): Unit =
      timers.startSingleTimer("checkoutTimer", Checkout.ExpireCheckoutCommand(), Timers.CHECKOUT_TIMER)

    private def startPaymentTimer(): Unit =
      timers.startSingleTimer("paymentTimer", Checkout.ExpirePaymentCommand(), Timers.PAYMENT_TIMER)
  }
}

