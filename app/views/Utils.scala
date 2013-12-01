package views

object Utils {

  def active[T](active: Option[T], current: T): String = {
    if (active.isDefined && active.get == current)
      "active"
    else
      ""
  }

}
