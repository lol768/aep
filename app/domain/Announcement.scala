package domain

import java.time.OffsetDateTime
import java.util.UUID

case class Announcement(
  id: UUID = UUID.randomUUID(),
  assessment: UUID,
  text: String,
  created: OffsetDateTime = OffsetDateTime.now(),
) {
  def asAnnouncementOrQuery = AnnouncementOrQuery(
    sender = Left("Announcement"),
    text = text,
    date = created,
  )
}
