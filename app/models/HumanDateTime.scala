package models

import org.joda.time.format._
import org.joda.time._
import java.util.Locale

object HumanDateTime {
  val LOCALE = Locale.forLanguageTag("nl")

  val FORMATTER_DATE_TIME = DateTimeFormat.forPattern("EEEE d MMM yyyy 'om' HH:mm").withLocale(LOCALE)

  val FORMATTER_DATE = DateTimeFormat.forPattern("EEEE d MMM yyyy").withLocale(LOCALE)

  def formatDateTime(date: DateTime): String = FORMATTER_DATE_TIME.print(date)

  def formatDate(date: DateTime): String = FORMATTER_DATE.print(date)


  val ONE_DAY = Duration.standardDays(1)
  val PERIOD_TYPE_HOUR_MINUTE = PeriodType.forFields(Array(DurationFieldType.hours(), DurationFieldType.minutes(), DurationFieldType.seconds()))
  val PERIOD_TYPE_YEAR_MONTH_DAY_HOUR = PeriodType.forFields(Array(DurationFieldType.years(), DurationFieldType.months(), DurationFieldType.days(), DurationFieldType.hours()))
  val VARIANTS = Array(" ", ",", ", en ", ", en ")
  val PERIOD_FORMATTER = new PeriodFormatterBuilder()
      .appendYears().appendSuffix(" jaar", " jaren")
      .appendSeparator(", ", " en ", VARIANTS).appendMonths().appendSuffix(" maand", " maanden")
      .appendSeparator(", ", " en ", VARIANTS).appendWeeks().appendSuffix(" week", " weken")
      .appendSeparator(", ", " en ", VARIANTS).appendDays().appendSuffix(" dag", " dagen")
      .appendSeparator(", ", " en ", VARIANTS).appendHours().appendSuffix(" uur", " uren")
      .appendSeparator(", ", " en ", VARIANTS).appendMinutes().appendSuffix(" minute", " minuten")
      .toFormatter.withLocale(LOCALE)

  def formatDuration(date: DateTime): String = formatDuration(DateTime.now(), date)
  def formatDuration(start: DateTime, end: DateTime): String = if (start.isBefore(end)) formatDuration(new Interval(start, end)) else ""

  def formatDuration(interval: Interval): String = PERIOD_FORMATTER.print(toDisplayPeriod(interval))

  def toDisplayPeriod(interval: Interval): Period = {
    val milis = interval.toDurationMillis

    if (milis < ONE_DAY.getMillis)
      interval.toPeriod(PERIOD_TYPE_HOUR_MINUTE)
    else
      interval.toPeriod(PERIOD_TYPE_YEAR_MONTH_DAY_HOUR)
  }
}
