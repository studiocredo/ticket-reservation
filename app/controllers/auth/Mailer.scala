package controllers.auth

import play.api.Play._
import play.api.templates.{Html, Txt}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.mvc.RequestHeader
import be.studiocredo.auth.EmailToken
import models.admin.{EventPrereservationsDetail, RichUser}
import models.entities.{OrderDetail, TicketDocument}

import scala.io.Source
import org.codemonkey.simplejavamail.{Email, TransportStrategy}
import javax.mail.internet.MimeUtility
import javax.mail.Message.RecipientType

case class Attachment(name: String, data: Source, mimeType: String)

object Mailer {
  val logger = Logger("mailer")

  val fromAddress: String = current.configuration.getString("smtp.from.address").get
  val fromName: String = current.configuration.getString("smtp.from.name").get
  val subjectPrefix = "[Studio Credo Ticket Reservatie]"
  val adminAddress: Option[String] = current.configuration.getString("mail.admin")

  val host: String = current.configuration.getString("smtp.host").getOrElse("localhost")
  val port: Int = current.configuration.getInt("smtp.port").getOrElse(25)
  val user: String = current.configuration.getString("smtp.user").getOrElse("")
  val password: String = current.configuration.getString("smtp.password").getOrElse("")

  val transportStrategy: TransportStrategy = current.configuration.getString("smtp.transport").getOrElse("plain") match {
    case "tls" => TransportStrategy.SMTP_TLS
    case "ssl" => TransportStrategy.SMTP_SSL
    case _ => TransportStrategy.SMTP_PLAIN
  }

  import org.codemonkey.simplejavamail.Mailer
  def mailer = new Mailer(host, port, user, password, transportStrategy)

  def sendSignUpEmail(to: String, token: EmailToken)(implicit request: RequestHeader)  {
    val txtAndHtml  = (None, Some(views.html.mails.signUpEmail(token.id)))

    sendEmail(s"$subjectPrefix Instructies voor registratie", to, txtAndHtml)
  }

  def sendPasswordResetEmail(to: String, users: List[RichUser], token: EmailToken)(implicit request: RequestHeader) {
    val txtAndHtml = (None, Some(views.html.mails.passwordReset(users, token.id)))
    sendEmail(s"$subjectPrefix Instructies om je wachtwoord te wijzigen", to, txtAndHtml)
  }

  def sendPasswordChangedNotification(to: String, user: RichUser)(implicit request: RequestHeader): Unit = {
    val txtAndHtml = (None, Some(views.html.mails.passwordChangedNotice(user)))
    sendEmail(s"$subjectPrefix  Wachtwoord gewijzigd", to, txtAndHtml)
  }

  def sendPrereservationSavedEmail(user: RichUser, maybePreres: Option[EventPrereservationsDetail])(implicit request: RequestHeader): Unit = {
    maybePreres match {
      case Some(preres) =>
        user.email match {
          case Some(email) =>
            val txtAndHtml = (None, Some(views.html.mails.prereservationSaved(user, preres)))
            sendEmail(s"$subjectPrefix Pre-reservaties geregistreerd", email, txtAndHtml)
          case None => ()
        }
      case None => ()
    }
  }

  def sendProfileCreatedEmail(user: RichUser)(implicit request: RequestHeader): Unit = {
    user.email match {
      case Some(email) =>
        val txtAndHtml = (None, Some(views.html.mails.profileCreated(user)))
        sendEmail(s"$subjectPrefix Gebruikersprofiel aangemaakt", email, txtAndHtml)
      case None => ()
    }
  }

  def sendProfileUpdatedEmail(user: RichUser)(implicit request: RequestHeader): Unit = {
    user.email match {
      case Some(email) =>
        val txtAndHtml = (None, Some(views.html.mails.profileUpdated(user)))
        sendEmail(s"$subjectPrefix Gebruikersprofiel gewijzigd", email, txtAndHtml)
      case None => ()
    }
  }

  def sendProfileActivationEmail(to: String, user: RichUser, token: EmailToken)(implicit request: RequestHeader) {
    val txtAndHtml = (None, Some(views.html.mails.profileActivation(user, token.id)))
    sendEmail(s"$subjectPrefix Instructies om je wachtwoord in te stellen", to, txtAndHtml)
  }

  def sendOrderConfirmationEmail(user: RichUser, order: OrderDetail): Unit = {
    user.email match {
      case Some(email) =>
        val txtAndHtml = (None, Some(views.html.mails.orderConfirmation(user, order)))
        sendEmail(s"$subjectPrefix Overzicht bestelling", email, txtAndHtml)
      case None => ()
    }
  }

  def sendOrderWithCommentsToAdmin(order: OrderDetail): Unit = {
    adminAddress match {
      case Some(email) =>
        val txtAndHtml = (None, Some(views.html.mails.orderWithComments(order)))
        sendEmail(s"$subjectPrefix Bestelling met commentaar", email, txtAndHtml)
      case None => ()
    }
  }

  def sendTicketEmail(user: RichUser, ticket: TicketDocument): Unit = {
    user.email match {
      case Some(email) =>
        val txtAndHtml = (None, Some(views.html.mails.ticket(user, ticket)))
        sendEmail(s"$subjectPrefix Tickets", email, txtAndHtml, List(Attachment(ticket.filename, Source.fromRawBytes(ticket.pdf), ticket.mimetype)))
      case None => ()
    }
  }

  private def sendEmail(subject: String, recipient: String, body: (Option[Txt], Option[Html]), attachments: List[Attachment] = Nil) {
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    if (Logger.isDebugEnabled) {
      logger.debug(s"sending email to '$recipient' = [[[$body]]]")
    }

    Akka.system.scheduler.scheduleOnce(1.seconds) {
      val e = new Email()
      e.setFromAddress(fromName, fromAddress)
      e.setSubject(subject)
      e.addRecipient(recipient, recipient, RecipientType.TO)
      e.setText(body._1.map(_.body).getOrElse(""))
      e.setTextHTML(body._2.getOrElse(Html("")).toString)
      attachments.foreach {
        a =>
          e.addAttachment(MimeUtility.encodeText(a.name), a.data.map(_.toByte).toArray, a.mimeType)
      }
      mailer.sendMail(e)
      logger.debug(s"Mail with subject $subject sent to $recipient")
    }
  }
}
