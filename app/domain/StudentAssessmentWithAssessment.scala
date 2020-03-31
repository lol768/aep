package domain

import java.time.Duration

import actors.WebSocketActor.AssessmentTimingInformation
import warwick.core.helpers.JavaTime

sealed trait BaseStudentAssessmentWithAssessment {
  def studentAssessment: BaseStudentAssessment
  def assessment: BaseAssessment

  def inProgress = studentAssessment.startTime.nonEmpty && studentAssessment.finaliseTime.isEmpty

  def getTimingInfo: AssessmentTimingInformation = {
    val now = JavaTime.offsetDateTime
    AssessmentTimingInformation(
      id = assessment.id,
      timeRemaining = if (inProgress) Some(assessment.duration.minus(Duration.between(studentAssessment.startTime.get, now)).toMillis) else None,
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
