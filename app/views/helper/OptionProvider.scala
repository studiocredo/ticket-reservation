package views.helper

import models.entities.Course

object Options {
  val CourseRenderer = Renderer[Course](_.id.get.toString, _.name)

  def apply[T](options: Seq[T], renderer: Renderer[T]) = new Options[T](options, renderer)
}

class Options[T](val options: Seq[T], render: Renderer[T]) {

  def contains(set: Set[String], value: Any): Boolean = set.contains(id(value))

  def display(value: Any): String = render.display(value.asInstanceOf[T])
  def id(value: Any): String = render.id(value.asInstanceOf[T])

  def map[B](f: T => B): Seq[B] = options map f
}


case class Renderer[T](id: (T => String), display: (T => String))
