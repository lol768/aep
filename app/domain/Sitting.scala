package domain

import java.time.Duration

import domain.BaseSitting.ProgressState
import enumeratum.{EnumEntry, PlayEnum}
import views.assessment.AssessmentTimingUpdate
import warwick.core.helpers.JavaTime

sealed trait BaseSitting {

  import domain.BaseSitting.ProgressState._

  val studentAssessment: BaseStudentAssessment

  val assessment: BaseAssessment

  val started: Boolean = studentAssessment.startTime.nonEmpty

  val finalised: Boolean = studentAssessment.hasFinalised

  val inProgress: Boolean = started && !finalised

  val uploadGraceDuration: Duration = Duration.ofMinutes(45)

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
    d.plus(uploadGraceDuration)
  }

  // Hard limit for student submitting, though they may be counted late.
  lazy val lateDuration: Option[Duration] = onTimeDuration.map { d =>
    d.plus(Assessment.lateSubmissionPeriod)
  }

  lazy val durationInfo: Option[DurationInfo] = duration.map { d => DurationInfo(d, onTimeDuration.get, lateDuration.get) }

  def canModify: Boolean = studentAssessment.startTime.exists(startTime =>
    lateDuration.exists { d =>
      !finalised &&
        startTime.plus(d).isAfter(JavaTime.offsetDateTime) &&
        !assessment.hasLastAllowedStartTimePassed
    }
  )

  def getTimingInfo: AssessmentTimingUpdate = {
    AssessmentTimingUpdate(
      id = assessment.id,
      windowStart = assessment.startTime,
      windowEnd = assessment.lastAllowedStartTime,
      start = studentAssessment.startTime,
      end = studentAssessment.startTime.flatMap(startTime => onTimeDuration.map(duration => startTime.plus(duration))),
      hasStarted = studentAssessment.startTime.nonEmpty,
      hasFinalised = studentAssessment.finaliseTime.nonEmpty,
      extraTimeAdjustment = studentAssessment.extraTimeAdjustment,
      showTimeRemaining = duration.isDefined,
      progressState = getProgressState,
    )
  }

  def getProgressState: Option[ProgressState] = {
    val now = JavaTime.offsetDateTime
    assessment.startTime.map { assessmentStartTime =>
      if (assessmentStartTime.isAfter(now)) {
        AssessmentNotYetOpen
      } else if (studentAssessment.startTime.isEmpty) {
        if (assessment.hasLastAllowedStartTimePassed) {
          NoShow
        } else {
          AssessmentOpenNotStarted
        }
      } else if (inProgress) {
        val studentStartTime = studentAssessment.startTime.get
        if (studentStartTime.plus(assessment.duration.get).isAfter(now)) {
          InProgress
        } else if (studentStartTime.plus(duration.get).isAfter(now)) {
          OnGracePeriod
        } else if (studentStartTime.plus(lateDuration.get).isAfter(now)) {
          Late
        } else {
          DeadlineMissed
        }
      } else {
        Finalised
      }
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

}

case class Sitting(
  studentAssessment: StudentAssessment,
  assessment: Assessment,
  declarations: Declarations
) extends BaseSitting

case class SittingMetadata(
  studentAssessment: StudentAssessmentMetadata,
  assessment: AssessmentMetadata
) extends BaseSitting
