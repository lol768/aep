package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import domain.SittingMetadata
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import services.tabula.{TabulaDepartmentService, TabulaStudentInformationService}
import services.{AssessmentClientNetworkActivityService, ReportingService, SecurityService}
import warwick.core.helpers.ServiceResults
import warwick.sso.{User, UserLookupService, Usercode}

import scala.concurrent.ExecutionContext

class InvigilatorAssessmentController @Inject()(
  security: SecurityService,
  reportingService: ReportingService,
  userLookup: UserLookupService,
  studentInformationService: TabulaStudentInformationService,
  tabulaDepartmentService: TabulaDepartmentService,
  messageService: MessageService,
  networkActivityService: AssessmentClientNetworkActivityService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def view(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>

    val assessment = req.assessment

    def makeUserNameMap(user: User): (Usercode, String) = {
      user.usercode -> user.name.full.getOrElse(user.usercode.string)
    }

    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      reportingService.expectedSittings(assessmentId),
      messageService.findByAssessment(assessmentId),
    ).successFlatMap {
      case (departments, studentAssessments, queries) =>
        ServiceResults.zip(
          studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = studentAssessments.map(_.studentId))),
          networkActivityService.getLatestActivityFor(studentAssessments.map(_.studentAssessmentId))
        ).successMap { case (students, latestActivities) =>
            Ok(views.html.invigilation.assessment(
              assessment = assessment,
              sittingMetadata = studentAssessments.map(SittingMetadata(_, assessment.asAssessmentMetadata)),
              invigilators = userLookup
                .getUsers(assessment.invigilators.toSeq)
                .getOrElse(Nil)
                .map {
                  case (_, user) => user
                }
                .map(makeUserNameMap)
                .toMap,
              students = students,
              department = departments.find(_.code == assessment.departmentCode.string),
              studentsWithQueries = queries.map(_.client).distinct,
              latestActivities = latestActivities,
            ))
          }
    }
  }

}

