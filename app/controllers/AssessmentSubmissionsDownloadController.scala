package controllers

import domain.Assessment.Platform
import domain.{Assessment, JobKeys, Sitting, UploadedFileOwner}
import javax.inject.Singleton
import org.quartz.{JobBuilder, Scheduler, TriggerBuilder}
import play.api.mvc.Result
import services.{StudentAssessmentService, TimingInfoService, UploadedFileService}
import services.job.{GenerateAssessmentZipJob, GenerateAssessmentZipJobBuilder, JobResult}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
abstract class AssessmentSubmissionsDownloadController(
  scheduler: Scheduler,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  generateAssessmentZipJobBuilder: GenerateAssessmentZipJobBuilder,
  timingInfo: TimingInfoService,
  studentAssessmentService: StudentAssessmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  protected def doDownload(assessment: Assessment)(implicit request: AuthenticatedRequest[_]): Future[Result] = {
    studentAssessmentService.sittingsByAssessmentId(assessment.id).successFlatMap{ sittings =>
      val latestLastStartTimeInFuture = sittings.flatMap(_.lastAllowedStartTimeForStudent(timingInfo.lateSubmissionPeriod)).forall(_.isAfter(JavaTime.offsetDateTime.minus(Assessment.uploadProcessDuration)))
      if (latestLastStartTimeInFuture) {
        Future.successful(Ok(views.html.generatingZip(assessment, Some(s"Submissions are not available until ${Assessment.uploadProcessDuration} after the last allowed start time"))))
      } else if (!assessment.platform.contains(Platform.OnlineExams)) {
        Future.successful(Ok(views.html.generatingZip(assessment, Some("Submissions are not available for non-AEP assessments"))))
      } else {
        uploadedFileService.listWithOwner(assessment.id, UploadedFileOwner.AssessmentSubmissions).successFlatMap { files =>
          if (files.size >= 2) {
            Future.successful(Ok(views.html.generatingZip(assessment, Some(s"Expected just 1 file but was ${files.size}"))))
          } else if (files.size == 1) {
            uploadedFileControllerHelper.serveFile(files.head)
          } else {
            val jobKey = JobKeys.GenerateAssessmentZipJob(assessment.id).key
            val errorJobKey = JobKeys.toErrorJobKey(jobKey)

            if (scheduler.checkExists(errorJobKey)) {
              // Look to see if a previous job for this key failed (persisted under the error key)
              val errorJob = scheduler.getJobDetail(errorJobKey)
              val errorMessage = errorJob.getJobDataMap.getString(JobResult.FailedJobErrorDetailsKeyName)
              Future.successful(Ok(views.html.generatingZip(assessment, Some(errorMessage))))
            } else {
              if (!scheduler.checkExists(jobKey)) {
                // Look for an existing job to generate the zip file
                scheduler.scheduleJob(
                  JobBuilder.newJob(classOf[GenerateAssessmentZipJob])
                    .usingJobData("id", assessment.id.toString)
                    .usingJobData("usercode", currentUser().usercode.string)
                    .withIdentity(jobKey)
                    .build(),
                  TriggerBuilder.newTrigger()
                    .startNow()
                    .build()
                )
              }

              Future.successful(Ok(views.html.generatingZip(assessment, None)))
            }
          }
        }
      }
    }
  }

  protected def doSubmissionsCSV(assessment: Assessment, sittings: Seq[Sitting])(implicit request: AuthenticatedRequest[_]): Result = {
    Ok.chunked(csvSource(generateAssessmentZipJobBuilder.submissionsCSV(assessment, sittings))).as("text/csv")
  }

}
