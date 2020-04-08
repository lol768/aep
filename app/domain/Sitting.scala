package domain

import java.time.Duration

import views.assessment.AssessmentTimingUpdate
import warwick.core.helpers.JavaTime

sealed trait BaseSitting {
  def studentAssessment: BaseStudentAssessment
  def assessment: BaseAssessment

  def inProgress: Boolean = started && !finalised

  def started: Boolean = studentAssessment.startTime.nonEmpty
  def finalised: Boolean = studentAssessment.hasFinalised

  def uploadGraceDuration: Duration = Duration.ofMinutes(45)

  def isCurrentForStudent: Boolean = !finalised &&
    assessment.startTime.exists(_.isBefore(JavaTime.offsetDateTime)) &&
    assessment.lastAllowedStartTime.exists(_.isAfter(JavaTime.offsetDateTime))

  case class DurationInfo(durationWithExtraAdjustment:Option[Duration], onTimeDuration: Option[Duration], lateDuration: Option[Duration])

  // How long the student has to complete the assessment (excludes upload grace duration)
  lazy val duration: Option[Duration] = assessment.duration.map { d =>
    d.plus(studentAssessment.extraTimeAdjustment.getOrElse(Duration.ZERO))
  }

  // How long the student has to complete the assessment including submission uploads
  lazy val onTimeDuration: Option[Duration] = duration.map{ d =>
    d.plus(uploadGraceDuration)
  }

  // Hard limit for student submitting, though they may be counted late.
  lazy val durationIncludingLate: Option[Duration] = onTimeDuration.map { d =>
    d.plus(Assessment.lateSubmissionPeriod)
  }

  lazy val durationInfo:DurationInfo = DurationInfo(duration, onTimeDuration, durationIncludingLate)

  def canFinalise: Boolean = studentAssessment.startTime.exists(startTime =>
    durationIncludingLate.exists { d =>
      !finalised &&
        startTime.plus(d).isAfter(JavaTime.offsetDateTime) &&
        !assessment.hasLastAllowedStartTimePassed
    }
  )

  def getTimingInfo: AssessmentTimingUpdate = {
    AssessmentTimingUpdate(
      id = assessment.id,
      startTime = studentAssessment.startTime.map(_.toInstant.toEpochMilli),
      hasStarted = studentAssessment.startTime.nonEmpty,
      hasFinalised = studentAssessment.hasFinalised
    )
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
