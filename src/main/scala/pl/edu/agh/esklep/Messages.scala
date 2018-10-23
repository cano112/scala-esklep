package pl.edu.agh.esklep

import pl.edu.agh.esklep.Model.Item

case object Messages {
  sealed trait Message
  sealed trait Command extends Message
  sealed trait Event extends Message

  case object Shop {
    case class InitializeShopCommand() extends Command
    case class CloseShopCommand() extends Command

    case class ShopInitializedEvent() extends Event
    case class ShopClosedEvent() extends Event
  }

  case object Cart {
    case class RemoveItemCommand(item: Item) extends Command
    case class AddItemCommand(item: Item) extends Command
    case class CloseCheckoutCommand() extends Command
    case class StartCheckoutCommand() extends Command
    case class CancelCheckoutCommand() extends Command
    case class ExpireCartCommand() extends Command

    case class ItemRemovedEvent(items: Seq[Item], item: Item) extends Event
    case class ItemAddedEvent(items: Seq[Item], item: Item) extends Event
    case class CheckoutClosedEvent(items: Seq[Item]) extends Event
    case class CheckoutStartedEvent(items: Seq[Item]) extends Event
    case class CheckoutCancelledEvent(items: Seq[Item]) extends Event
    case class TimerExpiredEvent() extends Event
  }

  case object Checkout {
    case class StartCheckoutCommand() extends Command
    case class CancelCheckoutCommand() extends Command
    case class SelectDeliveryMethodCommand() extends Command
    case class SelectPaymentMethodCommand() extends Command
    case class ReceivePaymentCommand() extends Command
    case class ExpireCheckoutCommand() extends Command
    case class ExpirePaymentCommand() extends Command

    case class CheckoutStartedEvent() extends Event
    case class CheckoutCancelledEvent() extends Event
    case class DeliveryMethodSelectedEvent() extends Event
    case class PaymentMethodSelectedEvent() extends Event
    case class PaymentReceivedEvent() extends Event
    case class CheckoutExpiredEvent() extends Event
    case class PaymentExpiredEvent() extends Event
  }
}








