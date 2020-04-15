package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.twirl.api.Html

case class Announcement(
  id: UUID = UUID.randomUUID(),
  assessment: UUID,
  text: String,
  created: OffsetDateTime = OffsetDateTime.now(),
) {
  val html: Html = Html(warwick.core.views.utils.nl2br(text).body)
  def asAnnouncementOrQuery = AnnouncementOrQuery(
    sender = Left("Announcement"),
    text = text,
    date = created,
  )
}
