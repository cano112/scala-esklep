package pl.edu.agh.esklep

import akka.actor.{Actor, ActorSystem, PoisonPill, Props, Timers}
import akka.event.Logging
import pl.edu.agh.esklep.ActorsFSM.{CartActor, CheckoutActor}
import pl.edu.agh.esklep.Messages.Cart._
import pl.edu.agh.esklep.Messages.{Checkout, Event}
import pl.edu.agh.esklep.Messages.Shop.{CloseShopCommand, InitializeShopCommand}
import pl.edu.agh.esklep.Model.Item

import scala.concurrent.Await
import scala.concurrent.duration._

sealed class ShopActor extends Actor with Timers {
  val log = Logging(context.system, this)
  private val cartActor = context.actorOf(Props[CartActor], "cartActor")
  private val checkoutActor = context.actorOf(Props[CheckoutActor], "checkoutActor")

  override def receive: Receive = {
    case _: CloseShopCommand => self ! PoisonPill
    case _: InitializeShopCommand =>
      cartActor ! AddItemCommand(Item("Item1"))
      cartActor ! RemoveItemCommand(Item("Item1"))
      cartActor ! AddItemCommand(Item("Item2"))
      timers.startSingleTimer("WAIT_1", "CONTINUE_1", 1 seconds)
    case "CONTINUE_1" =>
      cartActor ! StartCheckoutCommand()
      cartActor ! CancelCheckoutCommand()
      cartActor ! StartCheckoutCommand()
      cartActor ! CloseCheckoutCommand()

      checkoutActor ! Checkout.StartCheckoutCommand()
      checkoutActor ! Checkout.SelectDeliveryMethodCommand()
      timers.startSingleTimer("WAIT_2", "CONTINUE_2", 6 seconds)
    case "CONTINUE_2" =>
      checkoutActor ! Checkout.SelectPaymentMethodCommand()
      timers.startSingleTimer("WAIT_3", "CONTINUE_3", 6 seconds)
    case "CONTINUE_3" =>
      checkoutActor ! Checkout.ReceivePaymentCommand()
    case e: Event =>
      log.debug("Event: {}", e)
  }
}
object Application extends App {
  val actorSystem = ActorSystem("eSklepSystem")
  val shopActor = actorSystem.actorOf(Props[ShopActor], "shopActor")
  shopActor ! InitializeShopCommand()
  Await.result(actorSystem.whenTerminated, Duration.Inf)
}
