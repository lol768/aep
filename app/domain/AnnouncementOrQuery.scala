package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.sso.UniversityID

case class AnnouncementOrQuery(
  sender: Either[String, UniversityID],
  text: String,
  date: OffsetDateTime,
) {
  def isAnnouncement = sender.isLeft
    case Left(_) => true
    case _ => false
  }

  def isQuery = !isAnnouncement
}
