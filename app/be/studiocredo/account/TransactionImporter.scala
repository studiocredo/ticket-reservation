package be.studiocredo.account

import java.io.File

import models.entities.PaymentEdit

trait TransactionImporter {
  val id: String
  def importFile(file: File): List[PaymentEdit]
}
