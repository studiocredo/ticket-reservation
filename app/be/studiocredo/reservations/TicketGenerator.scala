package be.studiocredo.reservations

import com.itextpdf.text._
import com.itextpdf.text.pdf._
import java.io.{InputStream, FileOutputStream, File, ByteArrayOutputStream}
import models.ids._
import java.net.URL
import play.api.Logger
import models.entities._
import scala.Some
import models.{CurrencyFormat, HumanDateTime}
import play.api.http.MimeTypes

import scala.collection.mutable

class TicketGenerator {

}

object TicketGenerator {
  val logger = Logger("be.studiocredo.ticketgenerator")
  val templateResourceFolder = "templates"

  def create(order: OrderDetail, ticket: TicketDistribution, url: String): Option[TicketDocument] = {
    val out = new ByteArrayOutputStream
    // Create output PDF
    val document = new Document(PageSize.A4)
   // Get template info
    val ticketInputStreamMap = getTicketInputStreams(order.ticketOrders.map(_.show))

    try {
      val writer = PdfWriter.getInstance(document, out)
      document.open()

      // Set metadata
      val title = order.ticketOrders.map(_.show.name).distinct.mkString(", ")
      document.addTitle(s"Tickets $title")
      document.addAuthor("Studio Credo vzw")
      document.addCreationDate()

      val canvas = writer.getDirectContent
      val helvetica = new Font(Font.FontFamily.HELVETICA, 12)
      val font = helvetica.getCalculatedBaseFont(false)

      val templateMap = ticketInputStreamMap.filter(_._2.isDefined).map { case (e, is) =>
        e -> writer.getImportedPage(new PdfReader(is.get), 1)
      }

      order.ticketSeatOrders.foreach { ticketSeatOrder =>

        document.newPage()
        val template = templateMap(ticketSeatOrder.show.eventId)
        writer.getDirectContentUnder.addTemplate(template, 0, 0)

        addText(order, ticketSeatOrder, ticket, canvas, font)
        addBarcode(new URL(url), document, canvas)

      }

      ticketInputStreamMap.values.filter(_.isDefined).map(_.get).foreach( try { _.close })
      document.close()

      Some(TicketDocument(order, s"ticket_${ticket.reference}.pdf", out.toByteArray, "application/pdf"))

    } catch {
      case e: Exception =>
        logger.error(s"Failed to create ticket for order ${order.id}", e)
        None
    } finally {
      try {
        out.close()
      }
    }
  }

  private def getTicketInputStreams(eventShows: scala.List[EventShow]): Map[EventId, Option[InputStream]] = {
    val templateMap = mutable.Map[EventId, Option[InputStream]]()
    eventShows.foreach { eventShow =>
      templateMap.getOrElseUpdate(eventShow.eventId, Option(this.getClass.getClassLoader.getResourceAsStream(scala.List(templateResourceFolder, eventShow.template.get).mkString("/"))))
    }
    templateMap.toMap
  }

  private def addBarcode(url: URL, document: Document, canvas: PdfContentByte) {
    val qrcode = new BarcodeQRCode(url.toExternalForm, 110, 110, null)
    val img = qrcode.getImage
    img.setAbsolutePosition(20f, 15f)
    document.add(img)
  }

  private def addText(order: OrderDetail, ticketSeatOrder: TicketSeatOrderDetail, ticket: TicketDistribution, canvas: PdfContentByte, font: BaseFont) {
    canvas.beginText()

    canvas.setFontAndSize(font, 14)
    canvas.showTextAlignedKerned(Element.ALIGN_LEFT, ticketSeatOrder.show.name, 50, 560, 0)
    canvas.showTextAlignedKerned(Element.ALIGN_LEFT, HumanDateTime.formatDateTime(ticketSeatOrder.show.date), 50, 530, 0)

    canvas.setFontAndSize(font, 12)
    canvas.showTextAlignedKerned(Element.ALIGN_LEFT, ticketSeatOrder.show.venueName, 50, 500, 0)

    canvas.setFontAndSize(font, 48)
    canvas.showTextAligned(Element.ALIGN_RIGHT, ticketSeatOrder.ticketSeatOrder.seat.name, 410, 515, 0)

    canvas.setFontAndSize(font, 12)
    canvas.showTextAlignedKerned(Element.ALIGN_LEFT, order.order.billingName, 50, 405, 0)
    canvas.showTextAlignedKerned(Element.ALIGN_LEFT, order.billingAddressLines.mkString(", "), 50, 370, 0)
    canvas.showTextAlignedKerned(Element.ALIGN_RIGHT, CurrencyFormat.format(ticketSeatOrder.price), 405, 405, 0)
    canvas.showTextAlignedKerned(Element.ALIGN_RIGHT, HumanDateTime.formatDateTimeCompact(order.order.date), 405, 360, 0)

    canvas.showTextAligned(Element.ALIGN_CENTER, ticket.reference, 250, 35, 0)

    canvas.endText()
  }
}
