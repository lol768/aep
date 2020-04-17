package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import domain.SittingMetadata
import domain.messaging.MessageSender
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import services.tabula.{TabulaDepartmentService, TabulaStudentInformationService}
import services.{AssessmentClientNetworkActivityService, ReportingService, SecurityService}
import warwick.core.helpers.ServiceResults
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.sso.{User, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvigilatorAssessmentController @Inject()(
  security: SecurityService,
  reportingService: ReportingService,
  userLookup: UserLookupService,
  studentInformationService: TabulaStudentInformationService,
  tabulaDepartmentService: TabulaDepartmentService,
  messageService: MessageService,
  networkActivityService: AssessmentClientNetworkActivityService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
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
      networkActivityService.getLatestInvigilatorActivityFor(assessmentId)
    ).successFlatMap {
      case (departments, studentAssessments, queries, invigilatorActivity) =>
        ServiceResults.zip(
          studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = studentAssessments.map(_.studentId))),
          networkActivityService.getLatestActivityFor(studentAssessments.map(_.id))
        ).successMap { case (students, latestActivities) =>
            Ok(views.html.invigilation.assessment(
              assessment = assessment,
              sittingMetadata = studentAssessments.map(SittingMetadata(_, assessment.asAssessmentMetadata)).sortBy(metadata =>
                students.get(metadata.studentAssessment.studentId).map(profile =>
                  (profile.lastName, profile.firstName, profile.universityID.string)
                )
              ),
              invigilatorActivities = invigilatorActivity.map(a => a.usercode.get -> a).toMap,
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
              queriesFromStudents = queries.filter(_.sender == MessageSender.Client),
              studentsWithQueries = queries.map(_.client).distinct,
              latestStudentActivities = latestActivities,
            ))
          }
    }
  }

  def getFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit request =>
    request.assessment.brief.files.find(_.id == fileId)
      .map(uploadedFileControllerHelper.serveFile)
      .getOrElse(Future.successful(NotFound("File not found")))
  }

}

