package pl.edu.agh.esklep

case object Model {
  case class Item(name: String)

  sealed trait State
  sealed trait Data

  case object Cart {
    case object Empty extends State
    case object NonEmpty extends State
    case object InCheckout extends State

    case class Items(items: Seq[Item]) extends Data
  }

  case object Checkout {
    case object Idle extends State
    case object SelectingDelivery extends State
    case object SelectingPaymentMethod extends State
    case object ProcessingPayment extends State
    case object Cancelled extends State
    case object Closed extends State

    case object Nothing extends Data
  }
}
