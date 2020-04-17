package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.sso.Usercode
import play.twirl.api.Html

case class Announcement(
  id: UUID = UUID.randomUUID(),
  sender: Option[Usercode],
  assessment: UUID,
  text: String,
  created: OffsetDateTime = OffsetDateTime.now(),
) {
  val html: Html = Html(warwick.core.views.utils.nl2br(text).body)
  def asAnnouncementOrQuery = AnnouncementOrQuery(
    sender = Left(sender),
    text = text,
    date = created,
    isAnnouncement = true
  )
}
