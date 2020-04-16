package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.sso.{UniversityID, User, Usercode}

case class AnnouncementOrQuery(
  sender: Either[Usercode, UniversityID],
  text: String,
  date: OffsetDateTime,
  isAnnouncement: Boolean
) {
  def isQuery = !isAnnouncement
}
