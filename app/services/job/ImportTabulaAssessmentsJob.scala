package services.job

import javax.inject.Inject
import org.quartz._
import services.TabulaAssessmentImportService
import warwick.core.system.AuditLogContext

import scala.concurrent.{ExecutionContext, Future}

class ImportTabulaAssessmentsJob @Inject()(
  assessmentImportService: TabulaAssessmentImportService,
  scheduler: Scheduler,
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  override def getDescription(context: JobExecutionContext): String = "Import assessments"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    logger.info(s"Processing ${context.getJobDetail.getKey.toString} Scheduler Job")
    assessmentImportService.importAssessments().map(_.fold(
      errors => {
        val throwable = errors.flatMap(_.cause).headOption
        logger.error(s"Error Importing assessments", throwable.orNull)
        throw new JobExecutionException(throwable.orNull)
      },
      data => data
    )).map { assessmentImportResult =>
      if (assessmentImportResult.error) {
        throw new JobExecutionException("Error processing departmental import")
      } else {
        JobResult.success("completed")
      }
    }
  }

}
