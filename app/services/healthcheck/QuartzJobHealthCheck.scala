package services.healthcheck

import akka.actor.ActorSystem
import domain.JobKeys
import javax.inject.Inject
import org.quartz.Scheduler
import services.job.JobResult
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.Logging
import warwick.core.helpers.JavaTime.{localDateTime => now}

import scala.concurrent.duration._

class QuartzJobHealthCheck(jobKey: JobKeys.ByName)
  extends ServiceHealthcheckProvider(new ServiceHealthcheck(jobKey.healthCheckJobName, Status.Unknown, now)) with Logging {

  @Inject private var actorSystem: ActorSystem = _
  @Inject private var scheduler: Scheduler = _

  override def run(): Unit = {
    val errorMessage = Option(scheduler.getJobDetail(jobKey.key)).map(_.getJobDataMap.getString(JobResult.FailedJobErrorDetailsKeyName)).getOrElse("")
    val serviceHealthCheck = Option(scheduler.getJobDetail(jobKey.key)).map(_.getJobDataMap.getBoolean(JobResult.FailedJobKeyName)) match {
      case Some(true) => new ServiceHealthcheck(
        jobKey.healthCheckJobName,
        ServiceHealthcheck.Status.Error,
        now,
        s"${jobKey.healthCheckJobName} job has failed $errorMessage"
      )
      case Some(false) => new ServiceHealthcheck(
        jobKey.healthCheckJobName,
        ServiceHealthcheck.Status.Okay,
        now,
        s"${jobKey.healthCheckJobName} job ran successfully"
      )
      case _ => new ServiceHealthcheck(
        jobKey.healthCheckJobName,
        ServiceHealthcheck.Status.Okay,
        now,
        s"${jobKey.healthCheckJobName} job has not run yet"
      )
    }
    update(serviceHealthCheck)
  }

  // Called by HealthChecksStartup (Guice has no PostConstruct support)
  def init(): Unit = {
    actorSystem.scheduler.scheduleAtFixedRate(0.seconds, interval = 30.minutes)(() => {
      try run()
      catch {
        case e: Throwable =>
          logger.error("Error in health check", e)
      }
    })(actorSystem.dispatcher)
  }

}
