package helpers

import java.time.OffsetDateTime
import warwick.core.helpers.JavaTime
import scala.language.implicitConversions

trait SimpleSemanticRelativeTime {
  def now: OffsetDateTime = JavaTime.offsetDateTime

  class SpeshInt(val i: Int) {
    def hours: HoursAndMinutes = HoursAndMinutes(i, 0)
    def hour: HoursAndMinutes = hours
    def minutes: HoursAndMinutes = HoursAndMinutes(0, i)
    def minute: HoursAndMinutes = minutes
  }

  case class HoursAndMinutes(h: Int, m: Int) {
    def and(ham: HoursAndMinutes): HoursAndMinutes = HoursAndMinutes(h + ham.h, m + ham.m)
    def ago: OffsetDateTime = now.minusHours(h.toLong).minusMinutes(m.toLong)
    def fromNow: OffsetDateTime = now.plusHours(h.toLong).plusMinutes(m.toLong)
  }

  implicit def intToSpeshInt(i: Int): SpeshInt = new SpeshInt(i)
}
