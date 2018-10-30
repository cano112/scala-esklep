package pl.edu.agh.esklep

import akka.actor.{ActorRef, LoggingFSM, Props}
import pl.edu.agh.esklep.CartActor.FSM._
import pl.edu.agh.esklep.CartActor.Messages._
import pl.edu.agh.esklep.Model._

import scala.concurrent.duration._

class CartActor extends LoggingFSM[CartState, CartData] {
  startWith(Empty, ItemsWithOrderManager(Nil, context.parent))

  when(Empty) {
    case Event(AddItemCommand(i), ItemsWithOrderManager(_, orderManagerActor)) =>
      val items = i :: Nil
      sender ! ItemAddedEvent(items, i)
      goto(NonEmpty) using ItemsWithOrderManager(items, orderManagerActor)
  }

  when(NonEmpty, stateTimeout = CartActor.Timers.CART_TIMER) {
    case Event(StartCheckoutCommand(), ItemsWithOrderManager(_, orderManager)) =>
      val checkoutActor = context.actorOf(Props[CheckoutActor], "checkoutActor")
      checkoutActor ! CheckoutActor.Messages.StartCheckoutCommand(orderManager)
      orderManager ! OrderManagerActor.Messages.StartCheckoutCommand(checkoutActor)
      goto(InCheckout)
    case Event(AddItemCommand(i), ItemsWithOrderManager(items, orderManager)) =>
      val newItems = items :+ i
      sender ! ItemAddedEvent(newItems, i)
      goto(NonEmpty) using ItemsWithOrderManager(newItems, orderManager)
    case Event(RemoveItemCommand(i), ItemsWithOrderManager(items, orderManager)) =>
      val newItems = items.filter(item => item != i)
      sender ! ItemRemovedEvent(newItems, i)
      goto(if (newItems.isEmpty) Empty else NonEmpty) using ItemsWithOrderManager(newItems, orderManager)
    case Event(StateTimeout, _) =>
      self ! ExpireCartCommand()
      stay
    case Event(ExpireCartCommand(), ItemsWithOrderManager(_, orderManager)) =>
      sender ! TimerExpiredEvent()
      goto(Empty) using ItemsWithOrderManager(Nil, orderManager)
  }

  when(InCheckout) {
    case Event(CloseCheckoutCommand(), ItemsWithOrderManager(items, orderManager)) =>
      sender ! CheckoutClosedEvent(items)
      orderManager ! OrderManagerActor.Messages.ResetCommand
      goto(Empty) using ItemsWithOrderManager(Nil, orderManager)
    case Event(CancelCheckoutCommand(), ItemsWithOrderManager(items, _)) =>
      sender ! CheckoutCancelledEvent(items)
      goto(NonEmpty)
  }
}

object CartActor {

  object Timers {
    val CART_TIMER: FiniteDuration = 5 seconds
  }

  object FSM {
    sealed trait CartState
    case object Empty      extends CartState
    case object NonEmpty   extends CartState
    case object InCheckout extends CartState

    sealed trait CartData
    case class ItemsWithOrderManager(items: Seq[Item], orderManagerActor: ActorRef) extends CartData

  }

  object Messages {
    sealed trait CartMessage

    sealed trait CartCommand                 extends CartMessage
    case class RemoveItemCommand(item: Item) extends CartCommand
    case class AddItemCommand(item: Item)    extends CartCommand
    case class CloseCheckoutCommand()        extends CartCommand
    case class StartCheckoutCommand()        extends CartCommand
    case class CancelCheckoutCommand()       extends CartCommand
    case class ExpireCartCommand()           extends CartCommand

    sealed trait CartEvent                                    extends CartMessage
    case class ItemRemovedEvent(items: Seq[Item], item: Item) extends CartEvent
    case class ItemAddedEvent(items: Seq[Item], item: Item)   extends CartEvent
    case class CheckoutClosedEvent(items: Seq[Item])          extends CartEvent
    case class CheckoutStartedEvent(checkoutActor: ActorRef)  extends CartEvent
    case class CheckoutCancelledEvent(items: Seq[Item])       extends CartEvent
    case class TimerExpiredEvent()                            extends CartEvent
  }

}
