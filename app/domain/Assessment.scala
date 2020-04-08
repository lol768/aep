package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.{AssessmentType, _}
import domain.dao.AssessmentsTables.StoredBrief
import enumeratum.{EnumEntry, PlayEnum}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

sealed trait BaseAssessment extends DefinesStartWindow {
  def id: UUID
  def paperCode: String
  def section: Option[String]
  def title: String
  def startTime: Option[OffsetDateTime]
  def duration: Option[Duration]
  def platform: Set[Platform]
  def assessmentType: AssessmentType
  def state: State
  def tabulaAssessmentId: Option[UUID]
  def examProfileCode: String
  def moduleCode: String
  def departmentCode: DepartmentCode
  def sequence: String //MAB sequence

  def isInFuture: Boolean = startTime.exists(_.isAfter(JavaTime.offsetDateTime))
}

trait DefinesStartWindow {
  // (earliest allowed) start time
  def startTime: Option[OffsetDateTime]

  def lastAllowedStartTime: Option[OffsetDateTime] = startTime.map(_.plus(Assessment.window))

  def hasLastAllowedStartTimePassed: Boolean = lastAllowedStartTime.exists(_.isBefore(JavaTime.offsetDateTime))
}

case class Assessment(
  id: UUID,
  paperCode: String,
  section: Option[String],
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Option[Duration],
  platform: Set[Platform],
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
      (this.startTime.getOrElse(OffsetDateTime.MAX), this.paperCode, this.section.getOrElse("")),
      (that.startTime.getOrElse(OffsetDateTime.MAX), that.paperCode, that.section.getOrElse(""))
    )
  }
}

case class AssessmentMetadata(
  id: UUID = UUID.randomUUID(),
  paperCode: String,
  section: Option[String],
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Option[Duration],
  platform: Set[Platform],
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
      val label = "Download & Submission through AEP"
    }

    case object Moodle extends Platform {
      val label = "Moodle"
    }

    case object QuestionmarkPerception extends Platform {
      val label = "QMP"
    }

    case object TabulaAssignment extends Platform {
      val label = "Submission through Tabula Assignment Management"
    }

    case object MyWBS extends Platform {
      val label = "My WBS"
    }

    val values: IndexedSeq[Platform] = findValues
  }

  sealed trait AssessmentType extends EnumEntry {
    val label: String
    val validDurations: Seq[Long]
  }

  object AssessmentType extends PlayEnum[AssessmentType] {

    case object OpenBook extends AssessmentType {
      val label = "Open Book Assessment"
      override val validDurations: Seq[Long] = Seq(120, 180, 1440)
    }

    case object OpenBookFileBased extends AssessmentType {
      val label = "Open Book Assessment, files based"
      override val validDurations: Seq[Long] = Seq(120, 180, 1440)
    }

    case object Spoken extends AssessmentType {
      val label = "Spoken Open Book Assessment"
      override val validDurations: Seq[Long] = Seq(120, 180)
    }

    case object MultipleChoice extends AssessmentType {
      val label = "MCQ"
      override val validDurations: Seq[Long] = Seq(120, 180)
    }

    case object Bespoke extends AssessmentType {
      val label = "Bespoke Option (only if previously agreed)"
      override val validDurations: Seq[Long] = Nil
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

  // Students are allowed 2 extra hours after the official finish time of the exam
  // for them to make submissions. Anything submitted during this period should be
  // marked as LATE though.
  // Updated in OE-148
  val lateSubmissionPeriod: Duration = Duration.ofHours(2)

  private[domain] val window: Duration = Duration.ofHours(24)

  sealed trait State extends EnumEntry {
    val label: String = entryName
    val cssClass: String = "label label-danger"
  }

  object State extends PlayEnum[State] {
    case object Imported extends State { override val label: String = "Needs setup" }
    case object Draft extends State { override val label: String = "Needs setup" }
    case object Approved extends State { override val label: String = "Ready"
      override val cssClass: String = "label label-success"
    }

    val values: IndexedSeq[State] = findValues
  }

}

