package services.refiners

import domain.StudentAssessmentWithAssessment
import warwick.sso.AuthenticatedRequest

class AssessmentSpecificRequest[A](val studentAssessmentWithAssessment: StudentAssessmentWithAssessment, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
}
