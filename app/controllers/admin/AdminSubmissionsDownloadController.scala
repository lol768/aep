package controllers.admin

import java.util.UUID

import controllers.AssessmentSubmissionsDownloadController
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, StudentAssessmentService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper

import scala.concurrent.ExecutionContext

@Singleton
class AdminSubmissionsDownloadController @Inject()(
  security: SecurityService,
  scheduler: Scheduler,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  studentAssessmentService: StudentAssessmentService,
)(implicit ec: ExecutionContext) extends AssessmentSubmissionsDownloadController(scheduler, uploadedFileService, uploadedFileControllerHelper) {

  import security._

  def download(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    doDownload(request.assessment)
  }

  def submissionsCSV(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    studentAssessmentService.sittingsByAssessmentId(assessmentId).successMap { sittings =>
      doSubmissionsCSV(request.assessment, sittings)
    }
  }

}
