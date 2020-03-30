package domain

import java.time.Duration

import actors.WebSocketActor.AssessmentTimingInformation
import warwick.core.helpers.JavaTime

sealed trait BaseStudentAssessmentWithAssessment {
  def studentAssessment: BaseStudentAssessment
  def assessment: BaseAssessment

  def inProgress: Boolean = started && !finalised

  def started: Boolean = studentAssessment.startTime.nonEmpty
  def finalised: Boolean = studentAssessment.finaliseTime.nonEmpty

  def getTimingInfo: AssessmentTimingInformation = {
    val now = JavaTime.offsetDateTime
    AssessmentTimingInformation(
      id = assessment.id,
      timeRemaining = if (inProgress) Some(assessment.duration.minus(Duration.between(studentAssessment.startTime.get, now)).toMillis) else None,
      timeSinceStart = if (inProgress) Some(Duration.between(studentAssessment.startTime.get, now).toMillis) else None,
      timeUntilStart = if (studentAssessment.startTime.isEmpty && !assessment.hasWindowPassed) Some(Duration.between(assessment.startTime.get, now).toMillis) else None,
      hasStarted = started,
      hasFinalised = finalised,
      hasWindowPassed = assessment.hasWindowPassed
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
