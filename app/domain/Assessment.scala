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
  def paperCode: String
  def section: Option[String]
  def title: String
  def startTime: Option[OffsetDateTime]
  def duration: Duration
  def platform: Platform
  def assessmentType: AssessmentType
  def state: State
  def tabulaAssessmentId: Option[UUID]
  def examProfileCode: String
  def moduleCode: String
  def departmentCode: DepartmentCode
  def sequence: String //MAB sequence

  def isInFuture: Boolean = startTime.exists(_.isAfter(JavaTime.offsetDateTime))
}

case class Assessment(
  id: UUID,
  paperCode: String,
  section: Option[String],
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Duration,
  platform: Platform,
  assessmentType: AssessmentType,
  brief: Brief,
  invigilators: Set[Usercode],
  state: State,
  tabulaAssessmentId: Option[UUID], //for assessments created within app directly this will be blank.
  examProfileCode: String,
  moduleCode: String,
  departmentCode: DepartmentCode,
  sequence: String
) extends BaseAssessment with Ordered[Assessment] {
  def asAssessmentMetadata: AssessmentMetadata = AssessmentMetadata(
    id,
    paperCode,
    section,
    title,
    startTime,
    duration,
    platform,
    assessmentType,
    state,
    tabulaAssessmentId,
    examProfileCode,
    moduleCode,
    departmentCode,
    sequence,
  )

  override def compare(that: Assessment): Int = {
    Ordering.Tuple3[OffsetDateTime, String, String].compare(
      (this.startTime.getOrElse(OffsetDateTime.MAX), this.paperCode, this.title),
      (that.startTime.getOrElse(OffsetDateTime.MAX), that.paperCode, that.title)
    )
  }
}

case class AssessmentMetadata(
  id: UUID = UUID.randomUUID(),
  paperCode: String,
  section: Option[String],
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Duration,
  platform: Platform,
  assessmentType: AssessmentType,
  state: State,
  tabulaAssessmentId: Option[UUID],
  examProfileCode: String,
  moduleCode: String,
  departmentCode: DepartmentCode,
  sequence: String,
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

    case object TabulaAssignment extends Platform {
      val label = "Tabula Assignment Management"
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

  // Students are allowed an extra hour after the official finish time of the exam
  // for them to make submissions. Anything submitted during this period should be
  // marked as LATE though.
  val lateSubmissionPeriod: Duration = Duration.ofHours(1)

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

