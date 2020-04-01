package services.refiners

import domain.Assessment
import warwick.sso.AuthenticatedRequest

class AssessmentSpecificRequest[A](val assessment: Assessment, request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
}
