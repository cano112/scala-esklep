package pl.edu.agh.esklep

import akka.actor.ActorSystem
import akka.testkit.{TestFSMRef, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pl.edu.agh.esklep.Model.{Delivery, Item, PaymentMethod}
import pl.edu.agh.esklep.OrderManagerActor.FSM._
import pl.edu.agh.esklep.OrderManagerActor.Messages._

import scala.concurrent.duration._
import akka.pattern.ask

class OrderManagerSpec
  extends TestKit(ActorSystem("OrderManagerSpec"))
  with WordSpecLike
  with BeforeAndAfterAll
  with ScalaFutures
  with Matchers {

  implicit val timeout: Timeout = 5 seconds

  "An order manager" must {
    "supervise whole order process" in {

      def sendMessageAndValidateState(
        orderManager: TestFSMRef[ManagerState, ManagerData, OrderManagerActor],
        message: ManagerCommand,
        expectedState: ManagerState
      ): Unit = {
        (orderManager ? message).mapTo[Ack].futureValue shouldBe Done
        orderManager.stateName shouldBe expectedState
      }

      val orderManager = TestFSMRef[ManagerState, ManagerData, OrderManagerActor](new OrderManagerActor)
      orderManager.stateName shouldBe Uninitialized

      sendMessageAndValidateState(orderManager, AddItemCommand(Item("rollerblades")), Open)

      sendMessageAndValidateState(orderManager, BuyCommand, InCheckout)

      val delivery      = Delivery("Post")
      val paymentMethod = PaymentMethod("Cheque")
      sendMessageAndValidateState(orderManager, SelectDeliveryPaymentMethodCommand(delivery, paymentMethod), InPayment)

      sendMessageAndValidateState(orderManager, PayCommand, Finished)
    }
  }

}
