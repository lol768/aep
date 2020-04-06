package services.healthcheck

import akka.actor.ActorSystem
import domain.JobKeys
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import services.job.JobResult
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.Logging
import warwick.core.helpers.JavaTime.{localDateTime => now}

import scala.concurrent.duration._

@Singleton
class ImportTabulaAssessmentsHealthCheck @Inject()(
  system: ActorSystem,
  scheduler: Scheduler
) extends ServiceHealthcheckProvider(new ServiceHealthcheck(JobKeys.ImportAssessmentJob.healthCheckJobName, Status.Unknown, now)) with Logging {

  private val name = JobKeys.ImportAssessmentJob.healthCheckJobName

  override def run(): Unit = {
    val serviceHealthCheck = Option(scheduler.getJobDetail(JobKeys.ImportAssessmentJob.key)).map(_.getJobDataMap.getBoolean(JobResult.FailedJobKeyName)) match {
      case Some(true) => new ServiceHealthcheck(
        name,
        ServiceHealthcheck.Status.Error,
        now,
        s"${name} job has failed"
      )
      case Some(false) => new ServiceHealthcheck(
        name,
        ServiceHealthcheck.Status.Okay,
        now,
        s"${name} job ran successfully"
      )
      case _ => new ServiceHealthcheck(
        name,
        ServiceHealthcheck.Status.Okay,
        now,
        s"${name} job has not run yet"
      )
    }
    update(serviceHealthCheck)
  }

  system.scheduler.scheduleAtFixedRate(0.seconds, interval = 30.minutes)(() => {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  })(system.dispatcher)

}
