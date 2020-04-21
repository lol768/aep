package domain

import java.util.UUID

import org.quartz.JobKey
import services.job.JobResult

object JobKeys {
  sealed trait ByName {
    val name: String
    def key: JobKey = new JobKey(name, "DEFAULT")
    def healthCheckJobName: String
  }

  def toErrorJobKey(original: JobKey) = new JobKey(s"${original.getName}-${JobResult.FailedJobKeyName}", original.getGroup)

  val tabulaAssessmentImportJobKey = new JobKey("ImportAssessment")

  case object ImportAssessmentJob extends ByName {
    override val name = "ImportAssessment"
    override val healthCheckJobName = "import-assessments"
  }

  case object SendAssessmentRemindersJob extends ByName {
    override val name = "SendAssessmentReminders"
    override val healthCheckJobName = "send-assessment-reminders"
  }

  case class GenerateAssessmentZipJob(assessmentId: UUID) extends ByName {
    override val name: String = assessmentId.toString
    override val key: JobKey = new JobKey(name, "GenerateAssessmentZip")
    override def healthCheckJobName: String = throw new IllegalArgumentException("Unscheduled job")
  }
}

