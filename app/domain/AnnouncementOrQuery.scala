package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.twirl.api.Html
import warwick.sso.UniversityID

case class AnnouncementOrQuery(
  sender: Either[String, UniversityID],
  text: String,
  date: OffsetDateTime,
) {
  def isAnnouncement: Boolean = sender.isLeft

  def isQuery: Boolean = !isAnnouncement

  val html: Html = Html(warwick.core.views.utils.nl2br(text).body)
}
