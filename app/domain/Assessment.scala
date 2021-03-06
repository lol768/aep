package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment._
import domain.dao.AssessmentsTables.StoredBrief
import enumeratum.{EnumEntry, PlayEnum}
import play.twirl.api.Html
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
  val durationStyle: Option[DurationStyle]
  val state: State
  val tabulaAssessmentId: Option[UUID]
  val examProfileCode: String
  val moduleCode: String
  val departmentCode: DepartmentCode
  val sequence: String //MAB sequence

  def isCurrent(latePeriodAllowance: Duration): Boolean = startTime.exists(_.isBefore(JavaTime.offsetDateTime)) && defaultLastAllowedStartTime(latePeriodAllowance).exists(_.isAfter(JavaTime.offsetDateTime))
  def isInFuture: Boolean = startTime.exists(_.isAfter(JavaTime.offsetDateTime))
  def isDownloadAvailable(latePeriodAllowance: Duration): Boolean = defaultLastAllowedStartTime(latePeriodAllowance).exists(_.isBefore(JavaTime.offsetDateTime.minus(uploadProcessDuration)))
}

trait DefinesStartWindow {
  // (earliest allowed) start time
  val startTime: Option[OffsetDateTime]
  val duration: Option[Duration]
  val durationStyle: Option[DurationStyle]

  // May be different for each student depending on extra time allowance - see similar method in Sitting
  def defaultLastAllowedStartTime(latePeriodAllowance: Duration): Option[OffsetDateTime] = durationStyle match {
    case Some(DurationStyle.DayWindow) => startTime.map(_.plus(Assessment.dayWindow))
    case Some(DurationStyle.FixedStart) => for {
        start <- startTime
        dur <- duration
      } yield {
        start
          .plus(dur)
          .plus(Assessment.uploadGraceDuration)
          .plus(latePeriodAllowance)
      }
    case _ => None
  }

  // May be different for each student depending on extra time allowance - see similar method in Sitting
  def hasDefaultLastAllowedStartTimePassed(latePeriodAllowance: Duration, referenceDate: OffsetDateTime = JavaTime.offsetDateTime): Boolean =
    defaultLastAllowedStartTime(latePeriodAllowance).exists(_.isBefore(referenceDate))

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
  durationStyle: Option[DurationStyle],
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
    durationStyle,
    brief.copy(files = Seq.empty),
    invigilators,
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
  durationStyle: Option[DurationStyle],
  briefWithoutFiles: Brief,
  invigilators: Set[Usercode],
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

  sealed trait DurationStyle extends EnumEntry {
    val label: String
    val shortLabel: Html
    val validDurations: Seq[Long]
  }
  object DurationStyle extends PlayEnum[DurationStyle] {
    /** 24 hour window to start */
    case object DayWindow extends DurationStyle {
      override val label: String = "Timed assessment to be completed within a 24 hours window"
      override val shortLabel: Html = Html("""
        <span data-toggle="tooltip" data-placement="top" title="%s">
          24 hour window
        </span>
      """.format(label))
      override val validDurations: Seq[Long] = Seq(60, 90, 120, 180)
    }
    /** No window, exam starts at fixed time */
    case object FixedStart extends DurationStyle {
      override val label: String = "Timed assessment starting at a set time"
      override val shortLabel: Html = Html("""
        <span data-toggle="tooltip" data-placement="top" title="%s">
          Fixed start time
        </span>
      """.format(label))
      override val validDurations: Seq[Long] = Seq(60, 90, 120, 180)
    }
    override def values: IndexedSeq[DurationStyle] = findValues
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

  val uploadGraceDuration: Duration = Duration.ofMinutes(45)

  private[domain] val dayWindow: Duration = Duration.ofHours(24)

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

