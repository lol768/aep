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

  def getTimingInfo: AssessmentTimingInformation = {
    val now = JavaTime.offsetDateTime
    val baseDuration = assessment.duration
    val (timeRemaining, extraTimeAdjustment) = studentAssessment.startTime match {
      case _ if !inProgress =>
        (None, None)
      case Some(studentStart) =>
        studentAssessment.extraTimeAdjustment.map { et => (
          Some(baseDuration.plus(et).minus(Duration.between(studentStart, now)).toMillis),
          Some(et.toMillis)
        )}.getOrElse(
          (Some(baseDuration.minus(Duration.between(studentStart, now)).toMillis), None)
        )
    }

    AssessmentTimingInformation(
      id = assessment.id,
      timeRemaining = timeRemaining,
      extraTimeAdjustment = extraTimeAdjustment,
      timeSinceStart = if (inProgress) Some(Duration.between(studentAssessment.startTime.get, now).toMillis) else None,
      timeUntilStart = if (studentAssessment.startTime.isEmpty && !assessment.hasWindowPassed) Some(Duration.between(now, assessment.startTime.get).toMillis) else None,
      timeUntilEndOfWindow = if (!studentAssessment.hasFinalised) assessment.endTime.map(Duration.between(now, _).toMillis) else None,
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
