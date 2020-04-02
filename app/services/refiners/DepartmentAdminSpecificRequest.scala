package services.refiners

import domain.StudentAssessmentWithAssessment
import domain.tabula.Department
import warwick.sso.AuthenticatedRequest

class DepartmentAdminSpecificRequest[A](val departmentCodesUserIsAdminFor: Seq[Department], request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
}
