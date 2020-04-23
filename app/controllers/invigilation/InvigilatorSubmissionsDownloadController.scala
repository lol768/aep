package controllers.invigilation

import java.util.UUID

import controllers.AssessmentSubmissionsDownloadController
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper

import scala.concurrent.ExecutionContext

@Singleton
class InvigilatorSubmissionsDownloadController @Inject()(
  security: SecurityService,
  scheduler: Scheduler,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit ec: ExecutionContext) extends AssessmentSubmissionsDownloadController(scheduler, uploadedFileService, uploadedFileControllerHelper) {

  import security._

  def download(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit request =>
    doDownload(request.assessment)
  }

}
