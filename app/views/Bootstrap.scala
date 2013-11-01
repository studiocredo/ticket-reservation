package views

import views.html.helper.FieldConstructor

object Bootstrap {
  implicit val myFields = FieldConstructor(views.html.helper.twitterBootstrapInput.render)
}
