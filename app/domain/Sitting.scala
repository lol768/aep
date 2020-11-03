package domain

import java.time.{Duration, OffsetDateTime}

import domain.Assessment.{DurationStyle, Platform}
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

  val totalExtraTime: Duration = studentAssessment.extraTimeAdjustment(assessment.duration.getOrElse(Duration.ofMinutes(0L)))
    .getOrElse(Duration.ofMinutes(0L))

  /** Finalised either explicitly or by the exam ending (as long as some files were uploaded) */
  def finalised(latePeriodAllowance: Duration): Boolean = finalisedTime(latePeriodAllowance).nonEmpty

  lazy val explicitlyFinalised: Boolean = studentAssessment.hasExplicitlyFinalised

  def finalisedTime(latePeriodAllowance: Duration): Option[OffsetDateTime] = studentAssessment.explicitFinaliseTime
    .orElse(studentAssessment.submissionTime.filter(_ => hasLateEndPassed(latePeriodAllowance)))

  def inProgress(latePeriodAllowance: Duration): Boolean = started && !finalised(latePeriodAllowance)

  def lastAllowedStartTimeForStudent(latePeriodAllowance: Duration): Option[OffsetDateTime] =
    assessment.defaultLastAllowedStartTime(latePeriodAllowance).map(_.plus(totalExtraTime))

  def hasLastAllowedStartTimeForStudentPassed(latePeriodAllowance: Duration, referenceDate: OffsetDateTime = JavaTime.offsetDateTime): Boolean =
    lastAllowedStartTimeForStudent(latePeriodAllowance).exists(_.isBefore(referenceDate))

  def isCurrentForStudent(latePeriodAllowance: Duration): Boolean = !finalised(latePeriodAllowance) && assessment.isCurrent(latePeriodAllowance)

  case class DurationInfo(durationWithExtraAdjustment: Duration, onTimeDuration: Duration, lateDuration: Duration)
  case class TimingInfo(startTime: OffsetDateTime, uploadGraceStart: OffsetDateTime, onTimeEnd: OffsetDateTime, lateEnd: OffsetDateTime)

  // How long the student has to complete the assessment (excludes upload grace duration)
  lazy val duration: Option[Duration] = assessment.duration.map { d =>
    d.plus(totalExtraTime)
  }

  // How long the student has to complete the assessment including submission uploads
  lazy val onTimeDuration: Option[Duration] = duration.map { d =>
    require(uploadGraceDuration != null, "uploadGraceDuration is null!")
    d.plus(uploadGraceDuration)
  }

  // Hard limit for student submitting, though they may be counted late.
  def lateDuration(latePeriodAllowance: Duration): Option[Duration] = onTimeDuration.map { d =>
    d.plus(latePeriodAllowance)
  }

  private def clampToWindow(time: OffsetDateTime, latePeriodAllowance: Duration): Option[OffsetDateTime] =
    (Seq(time) ++ lastAllowedStartTimeForStudent(latePeriodAllowance)).minOption

  /** The latest that you can submit and still be considered on time */
  def onTimeEnd(latePeriodAllowance: Duration): Option[OffsetDateTime] =
    for {
      start <- effectiveStartTime
      otDuration <- onTimeDuration
      time <- clampToWindow(start.plus(otDuration), latePeriodAllowance)
    } yield time


  /** The latest that you can submit _at all_ */
  def lateEnd(latePeriodAllowance: Duration): Option[OffsetDateTime] =
    for {
      start <- effectiveStartTime
      duration <- lateDuration(latePeriodAllowance)
      time <- clampToWindow(start.plus(duration), latePeriodAllowance)
    } yield time

  private def hasLateEndPassed(latePeriodAllowance: Duration): Boolean =
    lateEnd(latePeriodAllowance).exists(_.isBefore(JavaTime.offsetDateTime))

  lazy val effectiveStartTime: Option[OffsetDateTime] = assessment.durationStyle match {
    case Some(DurationStyle.DayWindow) => studentAssessment.startTime
    case Some(DurationStyle.FixedStart) => assessment.startTime
    case _ => None
  }

  def durationInfo(latePeriodAllowance: Duration): Option[DurationInfo] = duration.map { d =>
    DurationInfo(d, onTimeDuration.get, lateDuration(latePeriodAllowance).get)
  }

  def timingInfo(latePeriodAllowance: Duration): Option[TimingInfo] = for {
    d <- duration
    est <- effectiveStartTime
  } yield TimingInfo(
    startTime = est,
    uploadGraceStart = Seq(est.plus(d), onTimeEnd(latePeriodAllowance).get).min,
    onTimeEnd = onTimeEnd(latePeriodAllowance).get,
    lateEnd = lateEnd(latePeriodAllowance).get
  )

  def canModify(latePeriodAllowance: Duration, referenceDate: OffsetDateTime = JavaTime.offsetDateTime): Boolean = studentAssessment.startTime.exists(startTime =>
    lateDuration(latePeriodAllowance).exists { d =>
      !finalised(latePeriodAllowance) &&
        startTime.plus(d).isAfter(referenceDate) &&
        !hasLastAllowedStartTimeForStudentPassed(latePeriodAllowance, referenceDate)
    }
  )

  def getTimingInfo(latePeriodAllowance: Duration): AssessmentTimingUpdate = {
    AssessmentTimingUpdate(
      id = assessment.id,
      windowStart = assessment.startTime,
      windowEnd = lastAllowedStartTimeForStudent(latePeriodAllowance),
      lastRecommendedStart = Seq(onTimeDuration.flatMap(d => lastAllowedStartTimeForStudent(latePeriodAllowance).map(_.minus(d))), assessment.startTime).max,
      start = studentAssessment.startTime,
      end = onTimeEnd(latePeriodAllowance),
      hasStarted = studentAssessment.startTime.nonEmpty,
      hasFinalised = finalised(latePeriodAllowance),
      extraTimeAdjustment = assessment.duration.flatMap(studentAssessment.extraTimeAdjustment),
      showTimeRemaining = duration.isDefined,
      progressState = getProgressState(latePeriodAllowance),
      submissionState = getSubmissionState(latePeriodAllowance),
      durationStyle = assessment.durationStyle,
    )
  }

  def getSubmissionState(latePeriodAllowance: Duration): SubmissionState =
    studentAssessment.submissionTime match {
      case Some(submitTime) if onTimeEnd(latePeriodAllowance).exists(submitTime.isBefore) => SubmissionState.OnTime
      case Some(_) if onTimeEnd(latePeriodAllowance).isEmpty => SubmissionState.Submitted
      case Some(_) => SubmissionState.Late
      case None => SubmissionState.None
    }

  def getProgressState(latePeriodAllowance: Duration): Option[ProgressState] = {
    val now = JavaTime.offsetDateTime
    assessment.startTime.map { assessmentStartTime =>
      if (assessmentStartTime.isAfter(now)) {
        AssessmentNotYetOpen
      } else if (studentAssessment.startTime.isEmpty) {
        if (hasLastAllowedStartTimeForStudentPassed(latePeriodAllowance)) {
          NoShow
        } else {
          AssessmentOpenNotStarted
        }
      } else if (inProgress(latePeriodAllowance)) {
        if (!assessment.platform.contains(Platform.OnlineExams)) {
          Started
        } else if (hasLastAllowedStartTimeForStudentPassed(latePeriodAllowance)) {
          DeadlineMissed
        } else {
          val startTime = effectiveStartTime.get
          val inProgressState = for (ad <- assessment.duration; d <- onTimeDuration; ld <- lateDuration(latePeriodAllowance)) yield {
            if (startTime.plus(ad).isAfter(now)) {
              InProgress
            } else if (startTime.plus(d).isAfter(now)) {
              OnGracePeriod
            } else if (startTime.plus(ld).isAfter(now)) {
              Late
            } else {
              DeadlineMissed
            }
          }
          inProgressState.getOrElse(Started)
        }
      } else {
        Finalised
      }
    }
  }

  /** Summary for invigilators */
  def getSummaryStatusLabel(latePeriodAllowance: Duration): Option[String] = {
    lazy val submission = getSubmissionState(latePeriodAllowance)
    getProgressState(latePeriodAllowance) map {
      case ProgressState.Late if submission == SubmissionState.OnTime => "Submitted, not finalised"
      case ProgressState.Late if submission == SubmissionState.Late => "Submitted late, not finalised"
      case ProgressState.Finalised if submission == SubmissionState.Late => "Finalised (submitted late)"
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
