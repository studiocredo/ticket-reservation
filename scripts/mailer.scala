implicit val request = play.api.test.FakeRequest("GET", "/", play.api.test.FakeHeaders(List((play.api.http.HeaderNames.HOST,List("tickets.studiocredo.be")))), None)
import controllers.auth.Mailer

def notifyProfileCreated(users: List[RichUser] = us.listInactive) = {
	val length = users.length
	users.zipWithIndex.foreach{ case (user,index) =>
		Mailer.sendProfileCreatedEmail(user)
		println(s"Processed ${user.name} (${index+1} out of $length)")
	}
}
