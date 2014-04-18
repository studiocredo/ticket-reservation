package be.studiocredo.util

import org.joda.money.{CurrencyUnit, Money => JMoney}
import java.math.{BigDecimal => JBigDecimal, RoundingMode}

class Money(val underlying: JMoney) {

  def amount: BigDecimal = underlying.getAmount

  def plus(other: Money): Money = {
    new Money(underlying.plus(other.underlying))
  }

  def minus(other: Money): Money = {
    new Money(underlying.minus(other.underlying))
  }
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
