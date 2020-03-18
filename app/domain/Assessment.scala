package domain

import java.time.OffsetDateTime
import java.util.UUID
import Assessment.AssessmentType
import Assessment.Brief
import enumeratum.{EnumEntry, PlayEnum}
import warwick.fileuploads.UploadedFile

import java.time.Duration
import scala.collection.immutable

case class Assessment(
  id: UUID = UUID.randomUUID(),
  code: String,
  startTime: OffsetDateTime,
  duration: Duration,
  assessmentType: AssessmentType,
  brief: Brief,
)

object Assessment {
  sealed trait AssessmentType extends EnumEntry
  object AssessmentType extends PlayEnum[AssessmentType] {
    case object Moodle extends AssessmentType
    case object OnlineExams extends AssessmentType

    val values: immutable.IndexedSeq[AssessmentType] = findValues
  }

  case class Brief(
    text: Option[String],
    fileIds: Seq[UUID],
    url: Option[String],
  )
}

