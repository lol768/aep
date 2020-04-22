package views.tags

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.{LocalDate, OffsetDateTime}
import java.util.Locale

import warwick.core.helpers.JavaTime

object formatDate {
  private val datetimeFormatter =
    new DateTimeFormatterBuilder()
      .append(JavaTime.dateFullNoDayFormatter)
      .appendLiteral(" at ")
      .append(DateTimeFormatter.ofPattern("HH:mm"))
      .toFormatter(Locale.UK)

  private val compactDatetimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss")

  private val dateFormatter =
    new DateTimeFormatterBuilder()
      .append(JavaTime.dateFullNoDayFormatter)
      .toFormatter(Locale.UK)

  def dateTime(date: OffsetDateTime): String =
    date.format(datetimeFormatter)

  def sortableDateTime(date: OffsetDateTime): String =
    date.format(JavaTime.iSO8601DateFormat)

  def tabulaDateTime(date: OffsetDateTime): String =
    date.format(JavaTime.iSO8601DateFormat)

  def compactDateTime(date: OffsetDateTime): String =
    date.format(compactDatetimeFormatter)

  def date(date: LocalDate): String =
    date.format(dateFormatter)
}
