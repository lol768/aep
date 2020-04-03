package services.refiners

import domain.tabula.Department
import warwick.sso.AuthenticatedRequest

class DepartmentAdminRequest[A](val departments: Seq[Department], request: AuthenticatedRequest[A])
  extends AuthenticatedRequest[A](request.context, request.request) {
}
