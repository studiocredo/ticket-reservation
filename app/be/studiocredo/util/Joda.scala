package be.studiocredo.util

import org.joda.time.{LocalDate, DateTime}

object Joda {
  implicit val DateTimeOrdering = new Ordering[DateTime] {
    def compare(a: DateTime, b: DateTime) = a.compareTo(b)
  }

  implicit val LocalDateOrdering = new Ordering[LocalDate] {
    def compare(a: LocalDate, b: LocalDate) = a.compareTo(b)
  }
}
