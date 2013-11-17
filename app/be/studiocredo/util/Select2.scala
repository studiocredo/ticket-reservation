package be.studiocredo.util

import play.api.mvc.Request
import play.api.libs.json.{Json, JsObject}
import models.Page

object Select2 {
  case class Select2Query(query: String, limit: Int, page: Int)

  def parse(request: Request[_]): Option[Select2Query] = {
    for {
      query <- request.getQueryString("q")
      limit <- request.getQueryString("limit") map (_.toInt)
      page <- request.getQueryString("page") map (_.toInt)
    } yield Select2Query(query, limit, Math.max(0, page - 1))
  }

  def respond[T](page: Page[T])(id: T => String, text: T => String): JsObject = {
    Json.obj("total" -> page.total, "results" ->
      page.items.map(item => Json.obj("id" -> id(item), "text" -> text(item)))
    )
  }
}
