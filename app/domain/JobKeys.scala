package domain

import org.quartz.JobKey

object JobKeys {
  sealed trait ByName {
    val name: String
    def key: JobKey = new JobKey(name, "DEFAULT")
    def healthCheckJobName: String
  }

  case object ImportAssessmentJob extends ByName {
    override val name = "ImportAssessment"
    override val healthCheckJobName = "import-assessments"
  }

  case object SendAssessmentRemindersJob extends ByName {
    override val name = "SendAssessmentReminders"
    override val healthCheckJobName = "send-assessment-reminders"
  }
}

