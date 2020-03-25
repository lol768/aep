package domain

case class StudentAssessmentWithAssessment(
  studentAssessment: StudentAssessment,
  assessment: Assessment
) {
  def inProgress = studentAssessment.startTime.nonEmpty && studentAssessment.finaliseTime.isEmpty
}
