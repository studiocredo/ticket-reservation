package models

case class Page[A](items: Seq[A], page: Int, size: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)

  def isEmpty: Boolean = items.isEmpty
  def map[B](f: (A) => B): Page[B] = this.copy(items.map(f))
}

