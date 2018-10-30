package pl.edu.agh.esklep

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen}
import pl.edu.agh.esklep.CartActor.Messages.{AddItemCommand, ItemAddedEvent, ItemRemovedEvent, RemoveItemCommand}
import pl.edu.agh.esklep.Model.Item

class CartAsyncSpec
  extends TestKit(ActorSystem("CartSpec"))
  with FeatureSpecLike
  with BeforeAndAfterAll
  with ImplicitSender
  with GivenWhenThen {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  feature("Cart") {
    scenario("User adds new item to cart") {
      Given("a cart actor and an item")
      val cartActor = system.actorOf(Props[CartActor])
      val item      = Item("A")

      When("a new item is added")
      cartActor ! AddItemCommand(item)

      Then("'item added' confirmation should be send")
      expectMsg(ItemAddedEvent(item :: Nil, item))
    }

    scenario("User removes an item from the cart with only one item") {
      Given("a cart actor with an item")
      val cartActor = system.actorOf(Props[CartActor])
      val item      = Item("A")
      cartActor ! AddItemCommand(item)
      ignoreMsg {
        case _: ItemAddedEvent => true
      }

      When("an item is removed")
      cartActor ! RemoveItemCommand(item)

      Then("'item removed' confirmation should be send")
      expectMsg(ItemRemovedEvent(Nil, item))
    }

    scenario("User removes an item from the cart with two items") {
      Given("a cart actor with an item")
      val cartActor = system.actorOf(Props[CartActor])
      val itemA     = Item("A")
      val itemB     = Item("B")
      cartActor ! AddItemCommand(itemA)
      cartActor ! AddItemCommand(itemB)
      ignoreMsg {
        case _: ItemAddedEvent => true
      }

      When("an item is removed")
      cartActor ! RemoveItemCommand(itemA)

      Then("the cart should be in non-empty state")
      expectMsg(ItemRemovedEvent(itemB :: Nil, itemA))
    }
  }
}
