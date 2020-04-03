package services.refiners

import domain.Assessment
import domain.tabula.Department

class DepartmentAdminAssessmentRequest[A](
  val assessment: Assessment,
  departments: Seq[Department],
  request: DepartmentAdminRequest[A])
  extends DepartmentAdminRequest[A](departments, request) {
}
