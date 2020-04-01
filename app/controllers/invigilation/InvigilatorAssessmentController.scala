package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import domain.tabula
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.refiners.ActionRefiners
import services.tabula.TabulaDepartmentService
import services.{AssessmentService, ReportingService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.sso.{Name, UserLookupService, Usercode}

import scala.concurrent.ExecutionContext

class InvigilatorAssessmentController @Inject()(
  security: SecurityService,
  actionRefiners: ActionRefiners,
  assessmentService: AssessmentService,
  reportingService: ReportingService,
  userLookup: UserLookupService,
  studentAssessmentService: StudentAssessmentService,
  tabulaDepartmentService: TabulaDepartmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def view(assessmentId: UUID): Action[AnyContent] = InvigilatorAsseessmentAction(assessmentId).async { implicit req =>

    val assessment = req.assessment

    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      studentAssessmentService.byAssessmentId(assessmentId),
    ).successMap {
      case (departments, studentAssessments) =>
        Ok(views.html.invigilation.assessment(
          assessment = assessment,
          userNames = userLookup
            .getUsers(assessment.invigilators.toSeq)
            .getOrElse(Nil)
            .map {
              case (_, user) => user
            }
            .map { user =>
              user.usercode -> user.name.full.getOrElse(user.usercode.string)
            }
            .toMap,
          students = userLookup
            .getUsers(studentAssessments.map(_.studentId))
            .getOrElse(Nil)
            .map {
              case (_, user) => user
            }.map { user =>
            user.usercode -> user.name.full.getOrElse(user.usercode.string)
          }.toMap,
          department = departments.find { dept =>
            dept.code == assessment.departmentCode.string ||
              dept.parentDepartment.exists(_.code == assessment.departmentCode.string)
          },
        ))
    }
  }

}

