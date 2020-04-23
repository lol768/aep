package services.job

import domain.JobKeys
import javax.inject.Inject
import org.quartz._
import play.api.Configuration
import services.AssessmentService
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.system.AuditLogContext

import scala.concurrent.{ExecutionContext, Future}


class TriggerSubmissionUploadsJob @Inject()(
  configuration: Configuration,
  assessmentService: AssessmentService,
  scheduler: Scheduler,
)(implicit ec: ExecutionContext) extends AbstractJob(scheduler) {

  override def getDescription(context: JobExecutionContext): String = "Upload submissions"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    if(configuration.get[Boolean]("tabula.postSubmissions")) {
      logger.info(s"Triggering submission uploads for finished assessments")
      assessmentService.getFinishedWithUnsentSubmissions.successMapTo { assessments =>
        logger.info(s"Found ${assessments.length} finished assessments with submissions to send")
        assessments.foreach { assessmentMetadata =>

          val jobKey = JobKeys.UploadSubmissionsJob(assessmentMetadata.id).key
          val errorKey = JobKeys.toErrorJobKey(jobKey)

          if (scheduler.checkExists(errorKey)) {
            // don't schedule a new job if a failed one exists for this assessment
            // avoids ObjectAlreadyExistsException thrown when trying to save the error again with the same error key
            val errorMessage = scheduler.getJobDetail(errorKey).getJobDataMap.getString(JobResult.FailedJobErrorDetailsKeyName)
            logger.error(s"[${assessmentMetadata.id}] - unable to trigger submission upload - $errorMessage")
          } else if (!scheduler.checkExists(jobKey)) {
            // only start a new job if one isn't currently running for this assessment
            scheduler.scheduleJob(
              JobBuilder.newJob(classOf[UploadSubmissionsJob])
                .usingJobData("id", assessmentMetadata.id.toString)
                .withIdentity(jobKey)
                .build(),
              TriggerBuilder.newTrigger()
                .startNow()
                .build()
            )
          }
        }
        assessments
      }.map(_.fold(
        errors => {
          val throwable = errors.flatMap(_.cause).headOption
          logger.error(s"Error sending submissions to Tabula", throwable.orNull)
          throw new JobExecutionException(throwable.orNull)
        },
        data => data
      )).map(a => JobResult.success(s"Uploads triggered for ${a.length} assessments"))
    } else {
      Future.successful(JobResult.success("Tabula uploads not scheduled as per tabula.postSubmissions"))
    }
  }

}
