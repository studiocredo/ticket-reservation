package models

import models.admin.RichUser
import models.entities.OrderDetail

object OrderFormat {
  def format(user: RichUser, order: OrderDetail): String = {
    val userString = s"${user.id}"
    val orderString = s"${order.order.id}"
    prepend(userString, "0", 4) + "/" + prepend(orderString, "0", 5)
  }

  private def prepend(string: String, elem: String, size: Int): String = {
    string.reverse.padTo(size, elem).mkString.reverse
  }
}
