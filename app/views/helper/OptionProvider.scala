package views.helper

import models.entities.{Order, Venue}
import models.entities.PaymentType.PaymentType
import play.api.data.format.Formatter
import play.api.data.FormError

object Options {
  val VenueRenderer = Renderer[Venue](_.id.toString, _.name)
  val PaymentTypeRenderer = Renderer[PaymentType](value => value.toString, value => s"options.paymenttype.${value.toString}")
  val OrderRenderer = Renderer[Option[Order]](_.fold("")(_.id.toString), value => value.fold("(geen)")(value => s"${value.id} - ${value.billingName}"))

  def apply[T](options: Seq[T], renderer: Renderer[T]) = new Options[T](options, renderer)
}

class Options[T](val options: Seq[T], render: Renderer[T]) {

  def contains(set: Set[String], value: Any): Boolean = set.contains(id(value))

  def display(value: Any): String = render.display(value.asInstanceOf[T])
  def id(value: Any): String = render.id(value.asInstanceOf[T])

  def map[B](f: T => B): Seq[B] = options map f

  def simpleMap = map[(String,String)](v => (id(v), display(v)))
}


case class Renderer[T](id: (T => String), display: (T => String))

trait GenericOption {
  val id: String
  val value: String
}

object UserActiveOption {
  trait Option extends GenericOption

  case object Active   extends Option { val id = "A"; val value = "actief"      }
  case object Inactive extends Option { val id = "I"; val value = "niet actief" }
  case object Both     extends Option { val id = "B"; val value = "beide"        }

  val options = Seq(Active, Inactive, Both)
  val mapper = options.map( key => (key.id, key.value)).toSeq
  val default = Active

  implicit val UserActiveFormatter = new Formatter[Option] {
    val typeName = "user_active_option"
    override val format = Some((s"format.$typeName", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option] = {
      data.get(key).map { value =>
        options.find(_.id == value).map(Right(_)).getOrElse(error(key, s"error.$typeName.invalid"))
      }.getOrElse(error(key, s"error.$typeName.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, value: Option): Map[String, String] = {
      Map(key -> value.id)
    }
  }
}

object OrderPaidOption {
  trait Option extends GenericOption

  case object WithPayments extends Option { val id = "P"; val value = "met betalingen"      }
  case object NoPayments   extends Option { val id = "U"; val value = "zonder betalingen" }
  case object Both         extends Option { val id = "B"; val value = "beide"        }

  val options = Seq(WithPayments, NoPayments, Both)
  val mapper = options.map( key => (key.id, key.value)).toSeq
  val default = Both

  implicit val OrderPaidFormatter = new Formatter[Option] {
    val typeName = "order_paid_option"
    override val format = Some((s"format.$typeName", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option] = {
      data.get(key).map { value =>
        options.find(_.id == value).map(Right(_)).getOrElse(error(key, s"error.$typeName.invalid"))
      }.getOrElse(error(key, s"error.$typeName.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, value: Option): Map[String, String] = {
      Map(key -> value.id)
    }
  }
}

object PaymentRegisteredOption {
  trait Option extends GenericOption

  case object Registered    extends Option { val id = "R"; val value = "geregistreerd"      }
  case object Unregistered  extends Option { val id = "U"; val value = "niet geregistreerd" }
  case object Both          extends Option { val id = "B"; val value = "beide"        }

  val options = Seq(Registered, Unregistered, Both)
  val mapper = options.map( key => (key.id, key.value)).toSeq
  val default = Both

  implicit val PaymentRegisteredFormatter = new Formatter[Option] {
    val typeName = "payment_registered_option"
    override val format = Some((s"format.$typeName", Nil))

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option] = {
      data.get(key).map { value =>
        options.find(_.id == value).map(Right(_)).getOrElse(error(key, s"error.$typeName.invalid"))
      }.getOrElse(error(key, s"error.$typeName.missing"))
    }

    private def error(key: String, msg: String) = Left(List(new FormError(key, msg)))

    override def unbind(key: String, value: Option): Map[String, String] = {
      Map(key -> value.id)
    }
  }
}