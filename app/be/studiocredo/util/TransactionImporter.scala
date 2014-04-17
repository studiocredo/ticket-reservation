package be.studiocredo.util

import models.entities.PaymentEdit
import java.io.File

trait TransactionImporter {
  val id: String
  def importFile(file: File): List[PaymentEdit]
}
