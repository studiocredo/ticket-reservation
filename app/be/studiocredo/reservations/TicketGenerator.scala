package be.studiocredo.reservations

import com.itextpdf.text._
import com.itextpdf.text.pdf._
import java.io.{FileOutputStream, File, ByteArrayOutputStream}
import models.ids._
import java.net.URL
import play.api.Logger
import models.entities.TicketSeatOrderDetail
import models.entities.OrderDetail
import scala.Some
import models.{CurrencyFormat, HumanDateTime}

case class TicketDocument(order: OrderDetail, pdf: Array[Byte]) {
  def saveAs(file: File) {
    val out = new FileOutputStream(file)
    try { out.write(pdf) } finally { out.close() }
  }
}

class TicketGenerator {

}

object TicketGenerator {
  val logger = Logger("be.studiocredo.ticketgenerator")
  val templateResource = "templates/slotshow_2014_ticket_template2.pdf"

  def create(order: OrderDetail): Option[TicketDocument] = {
    val out = new ByteArrayOutputStream

    try {
      // Create output PDF
      val document = new Document(PageSize.A4)

      val writer = PdfWriter.getInstance(document, out)
      document.open()

      // Set metadata
      val title = order.ticketOrders.map(_.show.name).distinct.mkString(", ")
      document.addTitle(s"Tickets $title")
      document.addAuthor("Studio Credo vzw")
      document.addCreationDate()

      // Get template info
      val templateStream = this.getClass.getClassLoader.getResourceAsStream(templateResource)
      val templateReader = new PdfReader(templateStream)
      val template = writer.getImportedPage(templateReader, 1)

      val canvas = writer.getDirectContent
      val helvetica = new Font(Font.FontFamily.HELVETICA, 12)
      val font = helvetica.getCalculatedBaseFont(false)

      order.ticketSeatOrders.foreach { ticketSeatOrder =>

        document.newPage()
        canvas.addTemplate(template, 0, 0)

        addText(order, ticketSeatOrder, canvas, font)
        addBarcode(new URL(s"http://www.tickets.studiocredo.be/${order.id}"), document)

      }

      templateStream.close()
      document.close()

      Some(TicketDocument(order, out.toByteArray))

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

  private def addBarcode(url: URL, document: Document) {
    val qrcode = new BarcodeQRCode(url.toExternalForm, 1, 1, null)
    val img = qrcode.getImage
    img.scalePercent(250.0f)
    img.setAbsolutePosition(280f, 120f)
    document.add(img)
  }

  private def addText(order: OrderDetail, ticketSeatOrder: TicketSeatOrderDetail, canvas: PdfContentByte, font: BaseFont) {
    canvas.beginText()
    canvas.setFontAndSize(font, 12)
    canvas.showTextAligned(Element.ALIGN_LEFT, ticketSeatOrder.show.name, 400, 788, 0)
    canvas.showTextAligned(Element.ALIGN_RIGHT, HumanDateTime.formatDate(ticketSeatOrder.show.date), 400, 752, 0)
    canvas.showTextAligned(Element.ALIGN_CENTER, CurrencyFormat.format(ticketSeatOrder.price), 400, 716, 0)
    canvas.showTextAligned(Element.ALIGN_CENTER, order.order.billingName, 400, 650, 0)
    canvas.showTextAligned(Element.ALIGN_CENTER, order.order.billingAddress, 400, 600, 0)
    canvas.showTextAligned(Element.ALIGN_CENTER, HumanDateTime.formatDateTime(order.order.date), 400, 600, 0)

    canvas.setFontAndSize(font, 48)
    canvas.showTextAlignedKerned(Element.ALIGN_CENTER, ticketSeatOrder.ticketSeatOrder.seat.name, 400, 644, 0)
    canvas.endText()
  }
}