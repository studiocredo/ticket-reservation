package be.studiocredo.util

import models.entities.{PaymentType, PaymentEdit}
import java.io.File
import scala.collection.mutable.ListBuffer
import java.text.{DecimalFormat, DecimalFormatSymbols}
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import java.security.MessageDigest

object AXATransactionImporter {
  trait Key {
    val value: String
  }

  //afschrift, datum verrichting, datum valuta, datum boeking, bedrag, saldo rekening , omschrijving aard verrichting, rekening begunstigde, tegenpartij, naam terminal, plaats terminal, kaartnummer, mededeling, vervolg mededeling, detail verrichting
  case object Transcript extends Key { val value = "afschrift" }
  case object TransactionDate extends Key { val value = "datum verrichting" }
  case object Amount extends Key { val value = "bedrag" }
  case object Debtor extends Key { val value = "tegenpartij" }
  case object Message extends Key { val value = "mededeling" }
  case object Message2 extends Key { val value = "vervolg mededeling" }
  case object Detail extends Key { val value = "detail verrichting" }

  val keys: List[Key] = List(Transcript, TransactionDate, Amount, Debtor, Message, Message2, Detail)
  val mapper = keys.map( key => (key.value, key)).toMap
}

class AXATransactionImporter extends TransactionImporter {
  val id = "AXA"
  import AXATransactionImporter._

  override def importFile(file: File): List[PaymentEdit] = {
    import scala.io.Source
    
    //this is naive -> no escaping from ; delimiter
    val headers = Source.fromFile(file).getLines().drop(8).take(1).next().split(';').toList
    
    val rawPaymentValues = new ListBuffer[List[String]]

    var lastProcessedLine: Option[List[String]] = None
    for (line <- Source.fromFile(file).getLines().drop(9)) {
      val values = line.split(';').toList
      if (values.length == 1) {
        val actualLastLine = lastProcessedLine.get
        val newLine = actualLastLine.take(actualLastLine.length-1).toList ++ List(actualLastLine.last + "\n" + values(0))
        lastProcessedLine = Some(newLine)
      } else {
        if (lastProcessedLine.isDefined) {
          rawPaymentValues += lastProcessedLine.get.map(stripQuotes)
        }
        lastProcessedLine = Some(values)
      }
    }

    val rawPaymentValueMap = rawPaymentValues.toList.map { value =>
      value.zipWithIndex.collect{
        case (v, index) if mapper.contains(headers(index)) => (mapper(headers(index)),v)
      }.toMap
    }
    rawPaymentValueMap.map{ map =>
      PaymentEdit(PaymentType.WireTransfer, Some(getImportId(map)), None, map(Debtor), getAmount(map), Some(getMessage(map)), Some(map(Detail)), getDate(map), false)
    }.filter(pe => pe.amount.amount >= 0)
  }

  private def stripQuotes(s: String): String = {
    if(s.startsWith("\"") && s.endsWith("\"")) s.dropRight(1).drop(1)
    else s
  }

  private def getMessage(map: Map[Key, String]): String = {
    List(map(Message), map(Message2)).filterNot(_.isEmpty).mkString("\n")
  }

  private def getImportId(map: Map[Key, String]): String = {
    val message = id + "@" + List(map(Transcript),map(TransactionDate),map(Amount),map(Debtor),map(Message),map(Message2),map(Detail)).mkString(";")
    MessageDigest.getInstance("MD5").digest(message.getBytes).map("%02X".format(_)).mkString
  }
  
  private def getAmount(map: Map[Key, String]): Money = {
    val otherSymbols = new DecimalFormatSymbols()
    otherSymbols.setDecimalSeparator(',')
    otherSymbols.setGroupingSeparator('.')
    val decimalFormat = new DecimalFormat("#,###.##", otherSymbols)

    Money(decimalFormat.parse(map(Amount)).floatValue())
  }

  private def getDate(map: Map[Key, String]): DateTime = {
    DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(map(TransactionDate))
  }

}
