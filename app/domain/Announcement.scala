package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.sso.Usercode

case class Announcement(
  id: UUID = UUID.randomUUID(),
  sender: Usercode,
  assessment: UUID,
  text: String,
  created: OffsetDateTime = OffsetDateTime.now(),
) {
  def asAnnouncementOrQuery = {
    AnnouncementOrQuery(
      sender = Left(sender),
      text = text,
      date = created,
      isAnnouncement = true
    )
  }
}
