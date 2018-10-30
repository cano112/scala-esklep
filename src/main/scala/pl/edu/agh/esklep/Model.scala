package pl.edu.agh.esklep

case object Model {
  case class Item(name: String)
  case class Delivery(name: String)
  case class PaymentMethod(name: String)
}
