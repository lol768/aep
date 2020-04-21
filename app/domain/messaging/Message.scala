package domain.messaging

import java.time.OffsetDateTime
import java.util.UUID

import domain.{AnnouncementOrQuery, DatabaseOperation, StoredVersion, Versioned}
import enumeratum.{EnumEntry, PlayEnum}
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.sso.{UniversityID, User, Usercode}

/**
  * Private message which can be sent to an invigilator.
  * At present this is one-way only, with replies coming in the form of announcements to the entire cohort if appropriate.
  */
case class Message (
  id: UUID,
  text: String,
  sender: MessageSender,
  client: UniversityID,
  assessmentId: UUID,
  created: OffsetDateTime = JavaTime.offsetDateTime,
  version: OffsetDateTime = JavaTime.offsetDateTime,
) extends Versioned[Message] {
  override def atVersion(at: OffsetDateTime): Message = copy(version = at)

  override def storedVersion[B <: StoredVersion[Message]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
    MessageVersion(
      id,
      text,
      sender,
      client,
      assessmentId,
      created,
      version,
      operation,
      timestamp,
      ac.usercode
    ).asInstanceOf[B]

  def asMessageData(member: Option[User]) = MessageData(
    text = text,
    sender = sender,
    created = created
  )

  def asAnnouncementOrQuery = AnnouncementOrQuery(
    sender = Right(client),
    text = text,
    date = created,
    isAnnouncement = false
  )
}


case class MessageVersion (
  id: UUID,
  text: String,
  sender: MessageSender,
  client: UniversityID,
  assessmentId: UUID,
  created: OffsetDateTime,
  version: OffsetDateTime = JavaTime.offsetDateTime,
  operation: DatabaseOperation,
  timestamp: OffsetDateTime,
  auditUser: Option[Usercode]
) extends StoredVersion[Message]

/**
  * Just the data of a message required to save it. Other properties
  * are derived from other objects passed in to the service method.
  */
case class MessageSave (
  text: String,
  sender: MessageSender,
) {
  def toMessage(
    client: UniversityID,
    assessmentId: UUID,
  ): Message = Message(
    id = UUID.randomUUID(),
    text = this.text,
    sender = this.sender,
    client = client,
    assessmentId = assessmentId,
  )
}

/**
  * Just enough Message to render with.
  */
case class MessageData (
  text: String,
  sender: MessageSender,
  created: OffsetDateTime,
)


object MessageData {
  def tupled = (apply _).tupled

  // oldest first
  val dateOrdering: Ordering[MessageData] = Ordering.by[MessageData, OffsetDateTime](data => data.created)(JavaTime.dateTimeOrdering)

}

sealed trait MessageSender extends EnumEntry
object MessageSender extends PlayEnum[MessageSender] {
  case object Client extends MessageSender
  case object Team extends MessageSender

  val values: IndexedSeq[MessageSender] = findValues
}

