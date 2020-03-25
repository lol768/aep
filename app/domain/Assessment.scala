package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment._
import enumeratum.{EnumEntry, PlayEnum}
import warwick.fileuploads.UploadedFile

sealed trait BaseAssessment {
  def id: UUID
  def code: String
  def title: String
  def startTime: Option[OffsetDateTime]
  def duration: Duration
  def platform: Platform
  def assessmentType: AssessmentType
}

case class Assessment(
  id: UUID = UUID.randomUUID(),
  code: String,
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Duration,
  platform: Platform,
  assessmentType: AssessmentType,
  brief: Brief,
  invigilators: Seq[String],
) extends BaseAssessment

case class AssessmentMetadata(
  id: UUID = UUID.randomUUID(),
  code: String,
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Duration,
  platform: Platform,
  assessmentType: AssessmentType,
) extends BaseAssessment

object Assessment {
  sealed trait Platform extends EnumEntry

  object Platform extends PlayEnum[Platform] {
    case object Moodle extends Platform
    case object OnlineExams extends Platform
    case object QuestionmarkPerception extends Platform

    val values: IndexedSeq[Platform] = findValues
  }

  sealed trait AssessmentType extends EnumEntry

  object AssessmentType extends PlayEnum[AssessmentType] {
    case object OpenBook extends AssessmentType
    case object Spoken extends AssessmentType
    case object MultipleChoice extends AssessmentType

    val values: IndexedSeq[AssessmentType] = findValues
  }

  case class Brief(
    text: Option[String],
    files: Seq[UploadedFile],
    url: Option[String],
  )

  object Brief {
    def empty: Brief = Brief(None, Seq.empty, None)
  }

  val window: Duration = Duration.ofHours(8)
}

