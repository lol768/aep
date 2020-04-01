package domain

import java.time.{Duration, OffsetDateTime}

import actors.WebSocketActor.AssessmentTimingInformation
import warwick.core.helpers.JavaTime

sealed trait BaseStudentAssessmentWithAssessment {
  def studentAssessment: BaseStudentAssessment
  def assessment: BaseAssessment
  def started: Boolean = studentAssessment.startTime.nonEmpty
  def finalised: Boolean = studentAssessment.hasFinalised
  def inProgress: Boolean = started && !finalised

  val onTimeEndForStudent: Option[OffsetDateTime] = assessment.startTime.map { st =>
    st
      .plus(assessment.duration)
      .plus(studentAssessment.extraTimeAdjustment.getOrElse(Duration.ZERO))
  }

  val lateEndForStudent: Option[OffsetDateTime] =
    onTimeEndForStudent.map(_.plus(Assessment.lateSubmissionPeriod))

  def getTimingInfo: AssessmentTimingInformation = {
    val now = JavaTime.offsetDateTime

    val extraTimeAdjustment = studentAssessment.extraTimeAdjustment.map(_.toMillis)

    AssessmentTimingInformation(
      id = assessment.id,
      extraTimeAdjustment = extraTimeAdjustment,
      timeSinceStart = if (inProgress) Some(Duration.between(assessment.startTime.get, now).toMillis) else None,
      timeUntilStart = assessment.startTime.map(st => Duration.between(now, st).toMillis),
      timeUntilOnTimeEndForStudent = if (!studentAssessment.hasFinalised) onTimeEndForStudent.map(Duration.between(now, _).toMillis) else None,
      timeUntilLateEndForStudent = if (!studentAssessment.hasFinalised) lateEndForStudent.map(Duration.between(now, _).toMillis) else None,
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
