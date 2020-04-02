package services.refiners

import domain.Assessment
import domain.tabula.Department
import warwick.sso.AuthenticatedRequest

class DepartmentAdminAssessmentSpecificRequest[A](
  val assessment: Assessment,
  departmentCodesUserIsAdminFor: Seq[Department],
  request: DepartmentAdminSpecificRequest[A])
  extends DepartmentAdminSpecificRequest[A](departmentCodesUserIsAdminFor, request) {
}
