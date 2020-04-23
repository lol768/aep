package domain.messaging

import java.time.OffsetDateTime
import java.util.UUID

import domain.{DatabaseOperation, StoredVersion, Versioned}
import enumeratum.{EnumEntry, PlayEnum}
import play.twirl.api.Html
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.sso.{UniversityID, User, Usercode}

/**
  * Private message which can be sent to an invigilator.
  * If features.twoWayMessages is true this is two-way between a single student and invigilators
  */
case class Message (
  id: UUID,
  text: String,
  sender: MessageSender,
  student: UniversityID,
  assessmentId: UUID,
  staffId: Option[Usercode],
  created: OffsetDateTime = JavaTime.offsetDateTime,
  version: OffsetDateTime = JavaTime.offsetDateTime,
) extends Versioned[Message] {

  val html: Html = Html(warwick.core.views.utils.nl2br(text).body)

  override def atVersion(at: OffsetDateTime): Message = copy(version = at)

  override def storedVersion[B <: StoredVersion[Message]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
    MessageVersion(
      id,
      text,
      sender,
      student,
      assessmentId,
      staffId,
      created,
      version,
      operation,
      timestamp,
      ac.usercode
    ).asInstanceOf[B]

  def asMessageData(member: Option[User]): MessageData = MessageData(
    text = text,
    sender = sender,
    created = created
  )
}


case class MessageVersion (
  id: UUID,
  text: String,
  sender: MessageSender,
  student: UniversityID,
  assessmentId: UUID,
  staffId: Option[Usercode],
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
  staffId: Option[Usercode],
) {
  def toMessage(
    student: UniversityID,
    assessmentId: UUID,
  ): Message = Message(
    id = UUID.randomUUID(),
    text = this.text,
    sender = this.sender,
    student = student,
    assessmentId = assessmentId,
    staffId = staffId
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
  case object Student extends MessageSender
  case object Invigilator extends MessageSender

  val values: IndexedSeq[MessageSender] = findValues
  // TODO: Support old values in the DB. Can removed in the next deploy
  override lazy val namesToValuesMap: Map[String, MessageSender] =
    values.map(v => v.entryName -> v).toMap + ("Client" -> Student)
}

