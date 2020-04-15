package system

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Configuration

@ImplementedBy(classOf[ConfiguredFeatures])
trait Features {
  def importStudentExtraTime: Boolean

  def overwriteAssessmentTypeOnImport: Boolean
}

@Singleton
class ConfiguredFeatures @Inject()(private val configuration: Configuration) extends Features {
  override val importStudentExtraTime: Boolean = configuration.get[Boolean]("app.importStudentExtraTime")

  override val overwriteAssessmentTypeOnImport: Boolean = configuration.get[Boolean]("app.overwriteAssessmentTypeOnImport")
}
