package views.helper

import models.entities.{Order, Venue}
import models.entities.PaymentType.PaymentType

object Options {
  val VenueRenderer = Renderer[Venue](_.id.toString, _.name)
  val PaymentTypeRenderer = Renderer[PaymentType](value => value.toString, value => s"options.paymenttype.${value.toString}")
  val OrderRenderer = Renderer[Order](_.id.toString, value => s"${value.id} - ${value.billingName}")

  def apply[T](options: Seq[T], renderer: Renderer[T]) = new Options[T](options, renderer)
}

class Options[T](val options: Seq[T], render: Renderer[T]) {

  def contains(set: Set[String], value: Any): Boolean = set.contains(id(value))

  def display(value: Any): String = render.display(value.asInstanceOf[T])
  def id(value: Any): String = render.id(value.asInstanceOf[T])

  def map[B](f: T => B): Seq[B] = options map f
}


case class Renderer[T](id: (T => String), display: (T => String))
