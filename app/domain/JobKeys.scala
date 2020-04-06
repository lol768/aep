package domain

import org.quartz.JobKey

sealed trait ByName {
  val name: String

  def key: JobKey = new JobKey(name, "DEFAULT")

  def healthCheckJobName: String

}

object JobKeys {

  object ImportAssessmentJob extends ByName {
    val name = "ImportAssessment"
    val healthCheckJobName = "import-assessments"
  }

}

