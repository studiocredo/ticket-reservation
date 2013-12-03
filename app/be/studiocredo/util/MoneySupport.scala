package be.studiocredo.util

import org.joda.money.{CurrencyUnit, Money => JMoney}
import java.math.{BigDecimal => JBigDecimal, RoundingMode}

class Money(underlying: JMoney) {

  def amount: BigDecimal = underlying.getAmount
}

object Money {
  val CURRENCY = CurrencyUnit.EUR
  val ROUNDING = RoundingMode.HALF_UP
  
  def apply(value: BigDecimal): Money = {
    apply(value.underlying())
  }

  def apply(value: JBigDecimal): Money = {
    new Money(JMoney.of(CURRENCY, value, ROUNDING))
  }
}