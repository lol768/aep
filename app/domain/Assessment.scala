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
  val id: UUID
  val paperCode: String
  val section: Option[String]
  val title: String
  val startTime: Option[OffsetDateTime]
  val duration: Option[Duration]
  val platform: Set[Platform]
  val assessmentType: Option[AssessmentType]
  val state: State
  val tabulaAssessmentId: Option[UUID]
  val examProfileCode: String
  val moduleCode: String
  val departmentCode: DepartmentCode
  val sequence: String //MAB sequence

  def isInFuture: Boolean = startTime.exists(_.isAfter(JavaTime.offsetDateTime))
  def isDownloadAvailable: Boolean = platform.contains(Platform.OnlineExams) && lastAllowedStartTime.exists(_.isBefore(JavaTime.offsetDateTime.minusHours(1)))
}

trait DefinesStartWindow {
  // (earliest allowed) start time
  val startTime: Option[OffsetDateTime]

  val lastAllowedStartTime: Option[OffsetDateTime] = startTime.map(_.plus(Assessment.window))

  def hasLastAllowedStartTimePassed(referenceDate: OffsetDateTime = JavaTime.offsetDateTime): Boolean = lastAllowedStartTime.exists(_.isBefore(referenceDate))

  def hasStartTimePassed(referenceDate: OffsetDateTime = JavaTime.offsetDateTime): Boolean = startTime.exists(_.isBefore(referenceDate))
}

case class Assessment(
  id: UUID,
  paperCode: String,
  section: Option[String],
  title: String,
  startTime: Option[OffsetDateTime],
  duration: Option[Duration],
  platform: Set[Platform],
  assessmentType: Option[AssessmentType],
  brief: Brief,
  invigilators: Set[Usercode],
  state: State,
  tabulaAssessmentId: Option[UUID], //for assessments created within app directly this will be blank.
  tabulaAssignments: Set[UUID],
  examProfileCode: String,
  moduleCode: String,
  departmentCode: DepartmentCode,
  sequence: String,
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
    tabulaAssignments,
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
  assessmentType: Option[AssessmentType],
  state: State,
  tabulaAssessmentId: Option[UUID],
  tabulaAssignments: Set[UUID],
  examProfileCode: String,
  moduleCode: String,
  departmentCode: DepartmentCode,
  sequence: String,
) extends BaseAssessment

object Assessment {
  sealed trait Platform extends EnumEntry {
    val label: String
    val requiresUrl: Boolean
  }

  object Platform extends PlayEnum[Platform] {
    case object OnlineExams extends Platform {
      val label = "Download & Submission through AEP"
      override val requiresUrl: Boolean = false
    }

    case object Moodle extends Platform {
      val label = "Moodle"
      override val requiresUrl: Boolean = true
    }

    case object QuestionmarkPerception extends Platform {
      val label = "QMP"
      override val requiresUrl: Boolean = true
    }

    case object TabulaAssignment extends Platform {
      val label = "Submission through Tabula Assignment Management"
      override val requiresUrl: Boolean = true
    }

    case object MyWBS extends Platform {
      val label = "My WBS"
      override val requiresUrl: Boolean = true
    }

    val values: IndexedSeq[Platform] = findValues
  }

  sealed trait AssessmentType extends EnumEntry {
    val label: String
    val studentFriendlyLabel: String
    val validDurations: Seq[Long]
  }

  object AssessmentType extends PlayEnum[AssessmentType] {

    case object OpenBook extends AssessmentType {
      override val label: String = "Open Book Assessment"
      override val studentFriendlyLabel: String = "Open book"
      override val validDurations: Seq[Long] = Seq(120, 180, 1440)
    }

    case object OpenBookFileBased extends AssessmentType {
      override val label: String = "Open Book Assessment, files based"
      override val studentFriendlyLabel: String = "Open book (file-based)"
      override val validDurations: Seq[Long] = Seq(120, 180, 1440)
    }

    case object Spoken extends AssessmentType {
      override val label: String = "Spoken Open Book Assessment"
      override val studentFriendlyLabel: String = "Open book (spoken)"
      override val validDurations: Seq[Long] = Seq(120, 180)
    }

    case object MultipleChoice extends AssessmentType {
      override val label: String = "MCQ"
      override val studentFriendlyLabel: String = "Multiple choice"
      override val validDurations: Seq[Long] = Seq(120, 180)
    }

    case object Bespoke extends AssessmentType {
      override val label: String = "Bespoke Option (only if previously agreed)"
      override val studentFriendlyLabel: String = "Custom (see assessment brief)"
      override val validDurations: Seq[Long] = Nil
    }

    val values: IndexedSeq[AssessmentType] = findValues
  }

  case class Brief(
    text: Option[String],
    files: Seq[UploadedFile],
    urls: Map[Platform, String],
  ) {
    def toStoredBrief: StoredBrief = StoredBrief(
      text = text,
      fileIds = files.map(_.id),
      urls = urls
    )
  }

  object Brief {
    def empty: Brief = Brief(None, Seq.empty, Map.empty)
  }

  // Students are allowed 2 extra hours after the official finish time of the exam
  // for them to make submissions. Anything submitted during this period should be
  // marked as LATE though.
  // Updated in OE-148
  val lateSubmissionPeriod: Duration = Duration.ofHours(2)

  val uploadGraceDuration: Duration = Duration.ofMinutes(45)

  private[domain] val window: Duration = Duration.ofHours(24)

  // The amount of time we wait for in-progress uploads to finish before making submissions available
  val uploadProcessDuration: Duration = Duration.ofHours(1)

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

