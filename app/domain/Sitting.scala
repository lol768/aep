package domain

import java.time.{Duration, OffsetDateTime}

import domain.BaseSitting.{ProgressState, SubmissionState}
import enumeratum.{EnumEntry, PlayEnum}
import views.assessment.AssessmentTimingUpdate
import warwick.core.helpers.JavaTime

sealed trait BaseSitting {
  import domain.BaseSitting.ProgressState._
  import Assessment.uploadGraceDuration

  val studentAssessment: BaseStudentAssessment

  val assessment: BaseAssessment

  val started: Boolean = studentAssessment.startTime.nonEmpty

  /** Finalised either explicitly or by the exam ending (as long as some files were uploaded) */
  lazy val finalised: Boolean = finalisedTime.nonEmpty

  lazy val explicitlyFinalised: Boolean = studentAssessment.hasExplicitlyFinalised

  lazy val finalisedTime: Option[OffsetDateTime] = studentAssessment.explicitFinaliseTime
    .orElse(studentAssessment.submissionTime.filter(_ => hasLateEndPassed))

  lazy val inProgress: Boolean = started && !finalised

  def isCurrentForStudent: Boolean = !finalised &&
    assessment.startTime.exists(_.isBefore(JavaTime.offsetDateTime)) &&
    assessment.lastAllowedStartTime.exists(_.isAfter(JavaTime.offsetDateTime))

  case class DurationInfo(durationWithExtraAdjustment: Duration, onTimeDuration: Duration, lateDuration: Duration)

  // How long the student has to complete the assessment (excludes upload grace duration)
  lazy val duration: Option[Duration] = assessment.duration.map { d =>
    d.plus(studentAssessment.extraTimeAdjustment.getOrElse(Duration.ZERO))
  }

  // How long the student has to complete the assessment including submission uploads
  lazy val onTimeDuration: Option[Duration] = duration.map { d =>
    require(uploadGraceDuration != null, "uploadGraceDuration is null!")
    d.plus(uploadGraceDuration)
  }

  // Hard limit for student submitting, though they may be counted late.
  lazy val lateDuration: Option[Duration] = onTimeDuration.map { d =>
    require(Assessment.lateSubmissionPeriod != null, "Assessment.lateSubmissionPeriod is null!")
    d.plus(Assessment.lateSubmissionPeriod)
  }

  private def clampToWindow(time: OffsetDateTime): Option[OffsetDateTime] =
    (Seq(time) ++ assessment.lastAllowedStartTime).minOption

  /** The latest that you can submit and still be considered on time */
  lazy val onTimeEnd: Option[OffsetDateTime] =
    for {
      start <- studentAssessment.startTime
      duration <- onTimeDuration
      time <- clampToWindow(start.plus(duration))
    } yield time


  /** The latest that you can submit _at all_ */
  lazy val lateEnd: Option[OffsetDateTime] =
    for {
      start <- studentAssessment.startTime
      duration <- lateDuration
      time <- clampToWindow(start.plus(duration))
    } yield time

  private def hasLateEndPassed: Boolean =
    lateEnd.exists(_.isBefore(JavaTime.offsetDateTime))

  lazy val durationInfo: Option[DurationInfo] = duration.map { d => DurationInfo(d, onTimeDuration.get, lateDuration.get) }

  def canModify(referenceDate: OffsetDateTime = JavaTime.offsetDateTime): Boolean = studentAssessment.startTime.exists(startTime =>
    lateDuration.exists { d =>
      !finalised &&
        startTime.plus(d).isAfter(referenceDate) &&
        !assessment.hasLastAllowedStartTimePassed(referenceDate)
    }
  )

  def getTimingInfo: AssessmentTimingUpdate = {
    AssessmentTimingUpdate(
      id = assessment.id,
      windowStart = assessment.startTime,
      windowEnd = assessment.lastAllowedStartTime,
      lastRecommendedStart = onTimeDuration.flatMap(d => assessment.lastAllowedStartTime.map(_.minus(d))),
      start = studentAssessment.startTime,
      end = onTimeEnd,
      hasStarted = studentAssessment.startTime.nonEmpty,
      hasFinalised = finalised,
      extraTimeAdjustment = studentAssessment.extraTimeAdjustment,
      showTimeRemaining = duration.isDefined,
      progressState = getProgressState,
      submissionState = getSubmissionState,
      durationStyle = assessment.durationStyle,
    )
  }

  lazy val getSubmissionState: SubmissionState =
    studentAssessment.submissionTime match {
      case Some(submitTime) if onTimeEnd.exists(submitTime.isBefore) => SubmissionState.OnTime
      case Some(_) if onTimeEnd.isEmpty => SubmissionState.Submitted
      case Some(_) => SubmissionState.Late
      case None => SubmissionState.None
    }

  def getProgressState: Option[ProgressState] = {
    val now = JavaTime.offsetDateTime
    assessment.startTime.map { assessmentStartTime =>
      if (assessmentStartTime.isAfter(now)) {
        AssessmentNotYetOpen
      } else if (studentAssessment.startTime.isEmpty) {
        if (assessment.hasLastAllowedStartTimePassed()) {
          NoShow
        } else {
          AssessmentOpenNotStarted
        }
      } else if (inProgress) {
        val studentStartTime = studentAssessment.startTime.get
        val inProgressState = for(ad <- assessment.duration; d <- onTimeDuration; ld <- lateDuration) yield {
          if (studentStartTime.plus(ad).isAfter(now)) {
            InProgress
          } else if (studentStartTime.plus(d).isAfter(now)) {
            OnGracePeriod
          } else if (studentStartTime.plus(ld).isAfter(now)) {
            Late
          } else {
            DeadlineMissed
          }
        }
        inProgressState.getOrElse(Started)
      } else {
        Finalised
      }
    }
  }

  /** Summary for invigilators */
  def getSummaryStatusLabel: Option[String] = {
    lazy val submission = getSubmissionState
    getProgressState map {
      case ProgressState.Late if submission == SubmissionState.OnTime => "Submitted, unfinalised"
      case ProgressState.Late if submission == SubmissionState.Late => "Submitted late, unfinalised"
      case other => other.label
    }

  }
}

object BaseSitting {

  sealed trait ProgressState extends EnumEntry {
    val label: String
  }

  object ProgressState extends PlayEnum[ProgressState] {

    case object AssessmentNotYetOpen extends ProgressState {
      val label = "Assessment is not open yet"
    }

    case object AssessmentOpenNotStarted extends ProgressState {
      val label = "Not started"
    }

    // OE-241 for bespoke assessments we don't have a duration so we only know if a student started
    case object Started extends ProgressState {
      val label = "Started"
    }

    case object InProgress extends ProgressState {
      val label = "In progress"
    }

    case object OnGracePeriod extends ProgressState {
      val label = "On grace period"
    }

    case object Late extends ProgressState {
      val label = "Running late"
    }

    case object Finalised extends ProgressState {
      val label = "Finalised"
    }

    case object DeadlineMissed extends ProgressState {
      val label = "Deadline missed"
    }

    case object NoShow extends ProgressState {
      val label = "No show"
    }

    val values: IndexedSeq[ProgressState] = findValues
  }

  /**
    * Describes how the student's currently submitted files will be considered,
    * if they don't upload any further files.
    *
    * Unlike progress state, this doesn't change over time - if your files are
    * on time, they stay like that, unless you upload more files.
    */
  sealed abstract class SubmissionState(val label: String) extends EnumEntry
  object SubmissionState extends PlayEnum[SubmissionState] {
    case object None extends SubmissionState(label = "None")
    /** Used if we have files but are missing a duration, so we can't be more specific */
    case object Submitted extends SubmissionState(label = "Submitted")
    case object OnTime extends SubmissionState(label = "On time")
    case object Late extends SubmissionState(label = "Late")
    val values: IndexedSeq[SubmissionState] = findValues
  }

}

case class Sitting(
  studentAssessment: StudentAssessment,
  assessment: Assessment,
  declarations: Declarations
) extends BaseSitting

case class SittingMetadata(
  studentAssessment: StudentAssessment,
  assessment: AssessmentMetadata
) extends BaseSitting
