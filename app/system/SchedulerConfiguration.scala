package system

import java.util.Date

import javax.inject.{Inject, Singleton}
import org.quartz.CronScheduleBuilder._
import org.quartz.TriggerBuilder._
import org.quartz._
import play.api.Configuration
import play.api.db.evolutions.ApplicationEvolutions
import services.job.ImportTabulaAssessmentsJob
import warwick.core.Logging

@Singleton
class SchedulerConfiguration @Inject()(
  evolutions: ApplicationEvolutions,
  configuration: Configuration,
)(implicit scheduler: Scheduler) extends Logging {
  configureScheduledJob(
    "ImportAssessment",
    JobBuilder.newJob(classOf[ImportTabulaAssessmentsJob]),
    CronScheduleBuilder.cronSchedule("0 30 * * * ?") // Every hour, xx:30
  )

  logger.info("Starting the scheduler")
  scheduler.start()

  def configureScheduledJob[SBT <: Trigger](name: String, jobBuilder: JobBuilder, schedule: ScheduleBuilder[SBT])(implicit scheduler: Scheduler): Option[Date] = {
    val jobKey = new JobKey(name)

    if (scheduler.getJobDetail(jobKey) == null) {
      val job = jobBuilder.withIdentity(jobKey).build()
      val trigger = newTrigger().withSchedule[SBT](schedule).build().asInstanceOf[Trigger]

      logger.info(s"Scheduling job: $name")
      Some(scheduler.scheduleJob(job, trigger))
    } else {
      logger.info(s"Job already scheduled: $name")
      None
    }
  }

  def cronFromProperty(key:String): CronScheduleBuilder = cronSchedule(new CronExpression(configuration.get[String](key)))
}
