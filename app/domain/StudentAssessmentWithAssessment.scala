package domain

case class StudentAssessmentWithAssessment(
  studentAssessment: StudentAssessment,
  assessment: Assessment
) {
  // TODO Add logic for when exam is finalised
  def inProgress = studentAssessment.startTime.nonEmpty && studentAssessment.finaliseTime.isEmpty
}
