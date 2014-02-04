package models

import org.joda.money.format._
import org.joda.money._
import be.studiocredo.util.Money
import java.util.Locale

object CurrencyFormat {
  val LOCALE = Locale.forLanguageTag("nl")

  val FORMATTER = new MoneyFormatterBuilder().appendLiteral("€").appendAmount(MoneyAmountStyle.ASCII_DECIMAL_POINT_NO_GROUPING).toFormatter(LOCALE);

  def format(money: Money): String = {
    FORMATTER.print(money.underlying)
  }
}
