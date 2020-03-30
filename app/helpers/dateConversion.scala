package helpers

import java.time.{Instant, LocalDateTime, OffsetDateTime, zone}

object dateConversion {
  import warwick.core.helpers.JavaTime.{timeZone => zone}

  implicit class localDateTimeConversion(ldt: LocalDateTime) {
    def asOffsetDateTime: OffsetDateTime = ldt.atOffset(zone.getRules.getOffset(ldt))
    def asInstant: Instant = ldt.toInstant(zone.getRules.getOffset(ldt))
  }

  implicit class instantConversion(instant: Instant) {
    def asOffsetDateTime: OffsetDateTime = instant.atOffset(zone.getRules.getOffset(instant))
  }
}
