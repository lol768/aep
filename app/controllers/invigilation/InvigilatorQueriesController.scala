package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.messaging.MessageService
import services.refiners.ActionRefiners
import services.tabula.TabulaDepartmentService
import services.{AssessmentService, ReportingService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.sso.{UniversityID, User, UserLookupService, Usercode}

import scala.concurrent.ExecutionContext

class InvigilatorQueriesController @Inject()(
  security: SecurityService,
  actionRefiners: ActionRefiners,
  assessmentService: AssessmentService,
  reportingService: ReportingService,
  userLookup: UserLookupService,
  studentAssessmentService: StudentAssessmentService,
  tabulaDepartmentService: TabulaDepartmentService,
  messageService: MessageService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def view(assessmentId: UUID, universityId: UniversityID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      messageService.findByStudentAssessment(assessmentId, universityId),
      studentAssessmentService.getMetadata(universityId, assessmentId)
    ).successMap {
      case (departments, queries, studentAssessmentMetadata) =>
        val student = userLookup.getUsers(Seq(universityId)).getOrElse(Map.empty)
        Ok(views.html.invigilation.studentQueries(
          req.assessment,
          studentAssessmentMetadata,
          queries,
          student,
          department = departments.find(_.code == req.assessment.departmentCode.string),
        ))
    }
  }

  def viewAll(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      messageService.findByAssessment(assessmentId),
    ).successMap {
      case (departments, queries) =>
        val students = userLookup.getUsers(queries.map(_.client)).getOrElse(Map.empty)
        Ok(views.html.invigilation.allQueries(
          req.assessment,
          queries,
          students,
          department = departments.find(_.code == req.assessment.departmentCode.string),
        ))
    }
  }

}

