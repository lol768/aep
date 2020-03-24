package domain

import warwick.core.helpers.JavaTime

case class StudentAssessmentWithAssessment(
  studentAssessment: StudentAssessment,
  assessment: Assessment
) {
  def isCurrentlyDoingExam = studentAssessment.startTime.exists(_.plus(assessment.duration).isBefore(JavaTime.offsetDateTime))
}
