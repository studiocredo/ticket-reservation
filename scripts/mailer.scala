implicit val request = play.api.test.FakeRequest("GET", "/", play.api.test.FakeHeaders(List((play.api.http.HeaderNames.HOST,List("tickets.studiocredo.be")))), None)
import controllers.auth.Mailer
