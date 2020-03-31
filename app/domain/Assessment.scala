package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment._
import domain.dao.AssessmentsTables.StoredBrief
import enumeratum.{EnumEntry, PlayEnum}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

sealed trait BaseAssessment {
  def id: UUID

  def code: String // paperCode currently.May be we need to rename this field?
  def title: String

  def startTime: Option[OffsetDateTime]

  def duration: Duration

  def platform: Platform

  def assessmentType: AssessmentType

  def state: State

  def tabulaAssessmentId: Option[UUID]

  def moduleCode: String

  def departmentCode: DepartmentCode

  def endTime: Option[OffsetDateTime] = startTime.map(_.plus(Assessment.window))

  def hasWindowPassed: Boolean = endTime.exists(_.isAfter(JavaTime.offsetDateTime))
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
  invigilators: Set[Usercode],
  state: State,
  tabulaAssessmentId: Option[UUID], //for assessments created within app directly this will be blank.
  moduleCode: String,
  departmentCode: DepartmentCode

) extends BaseAssessment

case class AssessmentMetadata(
  id: UUID = UUID.randomUUID(),
  code: String,
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Duration,
  platform: Platform,
  assessmentType: AssessmentType,
  state: State,
  tabulaAssessmentId: Option[UUID],
  moduleCode: String,
  departmentCode: DepartmentCode,
) extends BaseAssessment

object Assessment {

  sealed trait Platform extends EnumEntry {
    val label: String
  }

  object Platform extends PlayEnum[Platform] {

    case object OnlineExams extends Platform {
      val label = "Online Exams"
    }

    case object Moodle extends Platform {
      val label = "Moodle"
    }

    case object QuestionmarkPerception extends Platform {
      val label = "Questionmark Perception"
    }

    val values: IndexedSeq[Platform] = findValues
  }

  sealed trait AssessmentType extends EnumEntry {
    val label: String
  }

  object AssessmentType extends PlayEnum[AssessmentType] {

    case object OpenBook extends AssessmentType {
      val label = "Open book"
    }

    case object MultipleChoice extends AssessmentType {
      val label = "Multiple choice"
    }

    case object Spoken extends AssessmentType {
      val label = "Spoken"
    }

    val values: IndexedSeq[AssessmentType] = findValues
  }

  case class Brief(
    text: Option[String],
    files: Seq[UploadedFile],
    url: Option[String],
  ) {
    def toStoredBrief: StoredBrief = StoredBrief(
      text = text,
      fileIds = files.map(_.id),
      url = url
    )
  }

  object Brief {
    def empty: Brief = Brief(None, Seq.empty, None)
  }

  val window: Duration = Duration.ofHours(8)

  sealed trait State extends EnumEntry {
    val label: String = entryName
  }

  object State extends PlayEnum[State] {

    case object Draft extends State

    case object Submitted extends State

    case object Approved extends State

    case object Imported extends State

    val values: IndexedSeq[State] = findValues
  }

}

