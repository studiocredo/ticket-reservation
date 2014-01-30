package controllers.auth

import play.api.Play._
import play.api.templates.{Html, Txt}
import play.api.Logger
import play.api.libs.concurrent.Akka
import java.nio.charset.StandardCharsets
import play.api.mvc.RequestHeader
import be.studiocredo.auth.EmailToken
import models.admin.RichUser

object Mailer {
  val fromAddress = current.configuration.getString("smtp.from").get


  def sendSignUpEmail(to: String, token: EmailToken)(implicit request: RequestHeader)  {
    val txtAndHtml  = (None, Some(views.html.auth.mails.signUpEmail(token.id)))

    sendEmail("[Studio Credo Ticket Reservatie] Instructies voor registratie", to, txtAndHtml)
  }

  def sendPasswordResetEmail(to: String, users: List[RichUser], token: EmailToken)(implicit request: RequestHeader) {
    val txtAndHtml = (None, Some(views.html.auth.mails.passwordReset(users, token.id)))
    sendEmail("[Studio Credo Ticket Reservatie] Instructies om je wachtwoord te wijzigen", to, txtAndHtml)
  }

  def sendPasswordChangedNotification(to: String, user: RichUser)(implicit request: RequestHeader) = {
    val txtAndHtml = (None, Some(views.html.auth.mails.passwordChangedNotice(user)))
    sendEmail("[Studio Credo Ticket Reservatie] Wachtwoord gewijzigd", to, txtAndHtml)
  }


  private def sendEmail(subject: String, recipient: String, body: (Option[Txt], Option[Html])) {
    import com.typesafe.plugin._
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    if (Logger.isDebugEnabled) {
      Logger.debug(s"sending email to '$recipient' = [[[$body]]]")
    }

    Akka.system.scheduler.scheduleOnce(1.seconds) {
      val mail = use[MailerPlugin].email
      mail.setFrom(fromAddress)
      mail.setRecipient(recipient)
      mail.setSubject(subject)
      mail.setCharset(StandardCharsets.UTF_8.name())
      // the mailer plugin handles null / empty string gracefully
      mail.send(body._1.map(_.body).getOrElse(""), body._2.map(_.body).getOrElse(""))
    }
  }

}
