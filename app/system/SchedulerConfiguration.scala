package system

import java.util.Date

import domain.JobKeys
import javax.inject.{Inject, Singleton}
import org.quartz.CronScheduleBuilder._
import org.quartz.TriggerBuilder._
import org.quartz._
import play.api.Configuration
import play.api.db.evolutions.ApplicationEvolutions
import services.job.{ImportTabulaAssessmentsJob, SendAssessmentRemindersJob, TriggerSubmissionUploadsJob}
import warwick.core.Logging

@Singleton
class SchedulerConfiguration @Inject()(
  evolutions: ApplicationEvolutions,
  configuration: Configuration,
)(implicit scheduler: Scheduler) extends Logging {
  configureScheduledJob(
    JobKeys.ImportAssessmentJob.name,
    JobBuilder.newJob(classOf[ImportTabulaAssessmentsJob]),
    CronScheduleBuilder.cronSchedule("0 30 * * * ?") // Every hour, xx:30
  )

  configureScheduledJob(
    JobKeys.SendAssessmentRemindersJob.name,
    JobBuilder.newJob(classOf[SendAssessmentRemindersJob]),
    CronScheduleBuilder.cronSchedule("0 0 8 * * ?") // 8am every day
  )

  configureScheduledJob(
    JobKeys.TriggerSubmissionUploadsJob.name,
    JobBuilder.newJob(classOf[TriggerSubmissionUploadsJob]),
    CronScheduleBuilder.cronSchedule("0 * * * * ?") //  Every hour on the hour
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

  def configureUnscheduledJob(name: String, jobBuilder: JobBuilder)(implicit scheduler: Scheduler): Unit = {
    val jobKey = new JobKey(name)

    if (scheduler.getJobDetail(jobKey) == null) {
      val job = jobBuilder.withIdentity(jobKey).storeDurably(true).build()

      logger.info(s"Creating job: $name")
      scheduler.addJob(job, false)
    } else {
      logger.info(s"Job already exists: $name")
    }
  }
}
