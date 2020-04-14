package services.job

import helpers.ServiceResultUtils.traverseSerial
import javax.inject.Inject
import org.quartz.{JobExecutionContext, JobExecutionException, Scheduler}
import services.{AssessmentService, NotificationService}
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.system.AuditLogContext

import scala.concurrent.{ExecutionContext, Future}

class SendAssessmentRemindersJob @Inject()(
  assessmentService: AssessmentService,
  notificationService: NotificationService,
  scheduler: Scheduler,
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  override def getDescription(context: JobExecutionContext): String = "Send assessment reminders"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    logger.info(s"Processing ${context.getJobDetail.getKey.toString} Scheduler Job")

    assessmentService.getTodaysAssessments.successFlatMapTo { assessments =>
      traverseSerial(assessments.filter(_.startTime.nonEmpty))(notificationService.sendReminders)
    }.map(_.fold(
      errors => {
        val throwable = errors.flatMap(_.cause).headOption
        logger.error(s"Error sending assessment reminders", throwable.orNull)
        throw new JobExecutionException(throwable.orNull)
      },
      data => data
    )).map(_ => JobResult.success("completed"))
  }

}
