package domain

import java.time.Duration

import views.assessment.AssessmentTimingUpdate
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
  lazy val duration: Option[Duration] = assessment.duration.map { d =>
    d.plus(studentAssessment.extraTimeAdjustment.getOrElse(Duration.ZERO))
  }

  // Hard limit for student submitting, though they may be counted late.
  lazy val durationIncludingLate: Option[Duration] = duration.map { d => d.plus(Assessment.lateSubmissionPeriod) }

  def getTimingInfo: AssessmentTimingUpdate = {
    AssessmentTimingUpdate(
      id = assessment.id,
      startTime = studentAssessment.startTime.map(_.toInstant.toEpochMilli),
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
