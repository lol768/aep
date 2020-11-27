package controllers.admin

import java.util.UUID

import controllers.AssessmentSubmissionsDownloadController
import domain.{Assessment, DepartmentCode, Sitting}
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.mvc.{Action, AnyContent}
import services.job.GenerateAssessmentZipJobBuilder
import services.{AssessmentService, SecurityService, StudentAssessmentService, TimingInfoService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper

import scala.concurrent.ExecutionContext

@Singleton
class AdminSubmissionsDownloadController @Inject()(
  security: SecurityService,
  scheduler: Scheduler,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  generateAssessmentZipJobBuilder: GenerateAssessmentZipJobBuilder,
  timingInfo: TimingInfoService,
)(implicit ec: ExecutionContext) extends AssessmentSubmissionsDownloadController(scheduler, uploadedFileService, uploadedFileControllerHelper, generateAssessmentZipJobBuilder, timingInfo, studentAssessmentService) {

  import security._

  def download(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    doDownload(request.assessment)
  }

  def submissionsCSV(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    studentAssessmentService.sittingsByAssessmentId(assessmentId).successMap { sittings =>
      doSubmissionsCSV(request.assessment, sittings)
    }
  }

  def submissionsCSVDepartment(departmentCode: String): Action[AnyContent] = SpecificDepartmentAdminAction(departmentCode).async { implicit request =>
    assessmentService.getFinishedAssessmentsWithSittings(department = Some(DepartmentCode(departmentCode)), examProfileCode = None, importedOnly = false).successMap { assessments =>
      Ok.chunked(csvSource(generateAssessmentZipJobBuilder.multipleAssessmentSubmissionsCSV(assessments.map { case (a, s) => a -> s.toSeq }))).as("text/csv")
    }
  }

}
