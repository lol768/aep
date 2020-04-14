package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import domain.Assessment.Platform
import domain.{JobKeys, UploadedFileOwner}
import javax.inject.{Inject, Singleton}
import org.quartz.{JobBuilder, Scheduler, TriggerBuilder, TriggerKey}
import play.api.mvc.{Action, AnyContent}
import services.job.GenerateAssessmentZipJob
import services.{SecurityService, UploadedFileService}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFileControllerHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessmentSubmissionsDownloadController @Inject()(
  security: SecurityService,
  scheduler: Scheduler,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def download(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit request =>
    if (request.assessment.lastAllowedStartTime.forall(_.isAfter(JavaTime.offsetDateTime.minusHours(1)))) {
      Future.successful(BadRequest("Submissions are not available until 1 hour after the last allowed start time"))
    } else if (!request.assessment.platform.contains(Platform.OnlineExams)) {
      Future.successful(BadRequest("Submissions are not available for non-AEP assessments"))
    } else {
      uploadedFileService.listWithOwner(assessmentId, UploadedFileOwner.AssessmentSubmissions).successFlatMap { files =>
        if (files.size > 2) Future.successful(BadRequest(s"Expected just 1 file but was ${files.size}"))
        else if (files.size == 1) uploadedFileControllerHelper.serveFile(files.head)
        else {
          val jobKey = JobKeys.GenerateAssessmentZipJob(assessmentId).key

          // Look for an existing job to generate the zip file
          if (!scheduler.checkExists(jobKey)) {
            scheduler.scheduleJob(
              JobBuilder.newJob(classOf[GenerateAssessmentZipJob])
                .usingJobData("id", assessmentId.toString)
                .usingJobData("usercode", currentUser().usercode.string)
                .withIdentity(jobKey)
                .build(),
              TriggerBuilder.newTrigger()
                .startNow()
                .build()
            )
          }

          Future.successful(Ok(views.html.invigilation.generatingZip(request.assessment)))
        }
      }
    }
  }

}
