package domain

import warwick.core.helpers.JavaTime

case class StudentAssessmentWithAssessment(
  studentAssessment: StudentAssessment,
  assessment: Assessment
) {
  // TODO Add logic for when exam is finalised
  def isCurrentlyDoingExam = studentAssessment.startTime.nonEmpty
}
