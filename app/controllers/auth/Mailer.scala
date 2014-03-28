package controllers.auth

import play.api.Play._
import play.api.templates.{Html, Txt}
import play.api.Logger
import play.api.libs.concurrent.Akka
import java.nio.charset.StandardCharsets
import play.api.mvc.RequestHeader
import be.studiocredo.auth.EmailToken
import models.admin.{EventPrereservationsDetail, RichUser}
import models.entities.OrderDetail

object Mailer {
  val fromAddress = current.configuration.getString("smtp.from").get
  val subjectPrefix = "[Studio Credo Ticket Reservatie]"

  def sendSignUpEmail(to: String, token: EmailToken)(implicit request: RequestHeader)  {
    val txtAndHtml  = (None, Some(views.html.mails.signUpEmail(token.id)))

    sendEmail(s"$subjectPrefix Instructies voor registratie", to, txtAndHtml)
  }

  def sendPasswordResetEmail(to: String, users: List[RichUser], token: EmailToken)(implicit request: RequestHeader) {
    val txtAndHtml = (None, Some(views.html.mails.passwordReset(users, token.id)))
    sendEmail(s"$subjectPrefix Instructies om je wachtwoord te wijzigen", to, txtAndHtml)
  }

  def sendPasswordChangedNotification(to: String, user: RichUser)(implicit request: RequestHeader) = {
    val txtAndHtml = (None, Some(views.html.mails.passwordChangedNotice(user)))
    sendEmail(s"$subjectPrefix  Wachtwoord gewijzigd", to, txtAndHtml)
  }

  def sendPrereservationSavedEmail(user: RichUser, preres: Option[EventPrereservationsDetail])(implicit request: RequestHeader) = {
    preres match {
      case Some(preres) => {
        user.email match {
          case Some(email) => {
            val txtAndHtml = (None, Some(views.html.mails.prereservationSaved(user, preres)))
            sendEmail(s"$subjectPrefix Pre-reservaties geregistreerd", email, txtAndHtml)
          }
          case None => ()
        }
      }
      case None => ()
    }
  }

  def sendProfileCreatedEmail(user: RichUser)(implicit request: RequestHeader) = {
    user.email match {
      case Some(email) => {
        val txtAndHtml = (None, Some(views.html.mails.profileCreated(user)))
        sendEmail(s"$subjectPrefix Gebruikersprofiel aangemaakt", email, txtAndHtml)
      }
      case None => ()
    }
  }

  def sendProfileUpdatedEmail(user: RichUser)(implicit request: RequestHeader) = {
    user.email match {
      case Some(email) => {
        val txtAndHtml = (None, Some(views.html.mails.profileUpdated(user)))
        sendEmail(s"$subjectPrefix Gebruikersprofiel gewijzigd", email, txtAndHtml)
      }
      case None => ()
    }
  }

  def sendProfileActivationEmail(to: String, user: RichUser, token: EmailToken)(implicit request: RequestHeader) {
    val txtAndHtml = (None, Some(views.html.mails.profileActivation(user, token.id)))
    sendEmail(s"$subjectPrefix Instructies om je wachtwoord in te stellen", to, txtAndHtml)
  }

  def sendOrderConfirmationEmail(user: RichUser, order: OrderDetail)(implicit request: RequestHeader) = {
    user.email match {
      case Some(email) => {
        val txtAndHtml = (None, Some(views.html.mails.orderConfirmation(user, order)))
        sendEmail(s"$subjectPrefix Overzicht bestelling", email, txtAndHtml)
      }
      case None => ()
    }
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
