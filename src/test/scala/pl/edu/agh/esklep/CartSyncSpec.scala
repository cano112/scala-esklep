package pl.edu.agh.esklep

import akka.actor.ActorSystem
import akka.testkit.{TestFSMRef, TestKit}
import org.scalatest._
import pl.edu.agh.esklep.CartActor.FSM.{Empty, ItemsWithOrderManager, NonEmpty}
import pl.edu.agh.esklep.CartActor.Messages.{AddItemCommand, RemoveItemCommand}
import pl.edu.agh.esklep.Model.Item

class CartSyncSpec
  extends TestKit(ActorSystem("CartSpec"))
  with FeatureSpecLike
  with BeforeAndAfterAll
  with Matchers
  with GivenWhenThen {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  feature("Cart") {
    scenario("User adds new item to cart") {
      Given("a cart actor and an item")
      val cartActor = TestFSMRef(new CartActor)
      val item      = Item("A")

      When("a new item is added")
      cartActor ! AddItemCommand(item)

      Then("the cart should be in non-empty state")
      cartActor.stateName shouldBe NonEmpty

      And("the cart should contain only a given item")
      cartActor.stateData shouldBe an[ItemsWithOrderManager]
      val items = cartActor.stateData.asInstanceOf[ItemsWithOrderManager].items
      items should have size 1
      items should contain(item)
    }

    scenario("User removes an item from the cart with only one item") {
      Given("a cart actor with an item")
      val cartActor = TestFSMRef(new CartActor)
      val item      = Item("A")
      cartActor ! AddItemCommand(item)

      When("an item is removed")
      cartActor ! RemoveItemCommand(item)

      Then("the cart should be in empty state")
      cartActor.stateName shouldBe Empty

      And("the cart should not contain any item")
      cartActor.stateData shouldBe an[ItemsWithOrderManager]
      val items = cartActor.stateData.asInstanceOf[ItemsWithOrderManager].items
      items shouldBe empty
    }

    scenario("User removes an item from the cart with two items") {
      Given("a cart actor with an item")
      val cartActor = TestFSMRef(new CartActor)
      val itemA     = Item("A")
      val itemB     = Item("B")
      cartActor ! AddItemCommand(itemA)
      cartActor ! AddItemCommand(itemB)

      When("an item is removed")
      cartActor ! RemoveItemCommand(itemA)

      Then("the cart should be in non-empty state")
      cartActor.stateName shouldBe NonEmpty

      And("the cart should contain only one item left")
      cartActor.stateData shouldBe an[ItemsWithOrderManager]
      val items = cartActor.stateData.asInstanceOf[ItemsWithOrderManager].items
      items should have size 1
      items should contain(itemB)
    }
  }
}
