package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.twirl.api.Html
import warwick.sso.{UniversityID, Usercode}

case class AnnouncementOrQuery(
  sender: Either[Option[Usercode], UniversityID],
  text: String,
  date: OffsetDateTime,
  isAnnouncement: Boolean
) {
  def isQuery: Boolean = !isAnnouncement

  val html: Html = Html(warwick.core.views.utils.nl2br(text).body)
}
