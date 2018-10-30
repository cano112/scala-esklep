package pl.edu.agh.esklep

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen}
import pl.edu.agh.esklep.CartActor.Messages.{CartMessage, CloseCheckoutCommand}
import pl.edu.agh.esklep.CheckoutActor.Messages.{
  CheckoutMessage,
  ReceivePaymentCommand,
  SelectDeliveryMethodCommand,
  SelectPaymentMethodCommand,
  StartCheckoutCommand
}
import pl.edu.agh.esklep.Model.{Delivery, PaymentMethod}
import pl.edu.agh.esklep.OrderManagerActor.Messages.ManagerMessage

class CheckoutSpec
  extends TestKit(ActorSystem("CheckoutSpec"))
  with FeatureSpecLike
  with BeforeAndAfterAll
  with ImplicitSender
  with GivenWhenThen {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  feature("Checkout process") {
    scenario("Cart receives 'close checkout' command when checkout process finishes") {
      Given("a cart actor with checkout actor and order manager actors are set")
      val checkoutParent = TestProbe()
      val orderManager   = system.actorOf(Props[OrderManagerActor], "orderManager")
      val checkout       = checkoutParent.childActorOf(Props[CheckoutActor], "checkoutActor")

      When("user selects delivery, payment method and the payment is received")
      checkout ! StartCheckoutCommand(orderManager)
      checkout ! SelectDeliveryMethodCommand(Delivery("Post"))
      checkout ! SelectPaymentMethodCommand(PaymentMethod("Cheque"))
      checkout ! ReceivePaymentCommand()
      ignoreMsg {
        case _: CartMessage     => false
        case _: CheckoutMessage => true
        case _: ManagerMessage  => true
      }

      Then("cart receives 'close checkout' command")
      checkoutParent.expectMsg(CloseCheckoutCommand())
    }

  }
}
