package domain

import java.time.Duration

import actors.WebSocketActor.AssessmentTimingInformation
import warwick.core.helpers.JavaTime

sealed trait BaseStudentAssessmentWithAssessment {
  def studentAssessment: BaseStudentAssessment
  def assessment: BaseAssessment

  def inProgress: Boolean = started && !finalised

  def started: Boolean = studentAssessment.startTime.nonEmpty
  def finalised: Boolean = studentAssessment.hasFinalised

  def isCurrentForStudent: Boolean = !finalised &&
    assessment.startTime.exists(_.isBefore(JavaTime.offsetDateTime)) &&
    assessment.lastAllowedStartTime.exists(_.isAfter(JavaTime.offsetDateTime))

  // How long the student has to submit without being counted late
  lazy val duration = assessment.duration
    .plus(studentAssessment.extraTimeAdjustment.getOrElse(Duration.ZERO))

  // Hard limit for student submitting, though they may be counted late.
  lazy val durationIncludingLate = duration.plus(Assessment.lateSubmissionPeriod)

  def getTimingInfo: AssessmentTimingInformation = {
    val now = JavaTime.offsetDateTime

    val timeRemaining = studentAssessment.startTime match {
      case Some(studentStart) if inProgress =>
        Some(duration.minus(Duration.between(studentStart, now)).toMillis)
      case _ => None
    }

    AssessmentTimingInformation(
      id = assessment.id,
      timeRemaining = timeRemaining,
      extraTimeAdjustment = studentAssessment.extraTimeAdjustment.map(_.toMillis),
      timeSinceStart = if (inProgress) Some(Duration.between(studentAssessment.startTime.get, now).toMillis) else None,
      timeUntilStart = if (studentAssessment.startTime.isEmpty && !assessment.hasWindowPassed) Some(Duration.between(now, assessment.startTime.get).toMillis) else None,
      timeUntilEndOfWindow = if (!studentAssessment.hasFinalised) assessment.lastAllowedStartTime.map(Duration.between(now, _).toMillis) else None,
      hasStarted = studentAssessment.startTime.nonEmpty,
      hasFinalised = studentAssessment.hasFinalised
    )
  }
}

case class StudentAssessmentWithAssessment(
  studentAssessment: StudentAssessment,
  assessment: Assessment
) extends BaseStudentAssessmentWithAssessment

case class StudentAssessmentWithAssessmentMetadata(
  studentAssessment: StudentAssessmentMetadata,
  assessment: AssessmentMetadata
) extends BaseStudentAssessmentWithAssessment
