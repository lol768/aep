package services.refiners

import domain.Sitting
import warwick.sso.AuthenticatedRequest

class StudentAssessmentSpecificRequest[A](val sitting: Sitting, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
}
