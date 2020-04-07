package services.job

import java.util.Date

import org.quartz._
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}

sealed trait JobResult
object JobResult {
  case object quiet extends JobResult
  case class success(summary: String) extends JobResult
  val FailedJobKeyName = "jobFailed"
}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
abstract class AbstractJob(scheduler: Scheduler) extends Job with Logging {
  val timeout: Duration = Duration.Inf

  // Some jobs don't benefit from too much logging - they can do their own
  val doLog: Boolean = true

  // And some jobs are only meant to run once so failure tracking doesn't help with anything much
  val doFailureTracking: Boolean = true

  def run(implicit context: JobExecutionContext, audit: AuditLogContext): Future[JobResult]

  def getDescription(context: JobExecutionContext): String

  override final def execute(context: JobExecutionContext): Unit = {
    val audit = AuditLogContext.empty()
    val description = getDescription(context)
    try {
      if (doLog) logger.info(s"Starting job: $description")
      val result = Await.result(run(context, audit), timeout)
      if (doFailureTracking) context.getJobDetail.getJobDataMap.put(JobResult.FailedJobKeyName, false)
      if (doLog) logger.info(s"Completed job: $description with result: $result")
    } catch {
      // We log errors regardless of doLog
      case e: TimeoutException =>
        // NOTE - although we timed out waiting, the Future might still be executing.
        logger.error(s"Job timed out after ${timeout.toCoarsest}: $description")
        if (doFailureTracking) context.getJobDetail.getJobDataMap.put(JobResult.FailedJobKeyName, true)
        throw new JobExecutionException(e)
      case e: Exception =>
        logger.error(s"Error in job: $description", e)
        if (doFailureTracking) context.getJobDetail.getJobDataMap.put(JobResult.FailedJobKeyName, true)
        throw new JobExecutionException(e)
    }
  }

  def rescheduleFor(duration: Duration)(implicit context: JobExecutionContext): Unit = {
    val trigger =
      TriggerBuilder.newTrigger()
        .startAt(Date.from(JavaTime.instant.plusSeconds(duration.toSeconds)))
        .build()

    scheduler.rescheduleJob(context.getTrigger.getKey, trigger)
  }
}
