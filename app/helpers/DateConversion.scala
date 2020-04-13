package helpers

import java.time.{Instant, LocalDateTime, OffsetDateTime}
import warwick.core.helpers.JavaTime.{timeZone => zone}

object DateConversion {

  implicit class localDateTimeConversion(ldt: LocalDateTime) {
    def asOffsetDateTime: OffsetDateTime = ldt.atZone(zone).toOffsetDateTime
    def asInstant: Instant = ldt.atZone(zone).toInstant
  }

  implicit class instantConversion(instant: Instant) {
    def asOffsetDateTime: OffsetDateTime = instant.atZone(zone).toOffsetDateTime
  }
}
