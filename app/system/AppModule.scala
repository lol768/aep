package system

import controllers.UploadedFileErrorProviderImpl
import net.codingwell.scalaguice.ScalaModule
import org.quartz.Scheduler
import play.api.{Configuration, Environment}
import services.sandbox.DataGeneration
import services.tabula.{TabulaAssessmentService, TabulaAssessmentServiceImpl, TabulaDepartmentService, TabulaDepartmentServiceImpl, TabulaStudentInformationService, TabulaStudentInformationServiceImpl}
import warwick.fileuploads.UploadedFileErrorProvider

import scala.util.Random

class AppModule(environment: Environment, configuration: Configuration) extends ScalaModule {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind[Scheduler].toProvider[SchedulerProvider]
    bind[UploadedFileErrorProvider].to[UploadedFileErrorProviderImpl]
        bind(classOf[ClusterLifecycle]).asEagerSingleton()

    bind[TabulaAssessmentService]
      .annotatedWithName("TabulaAssessmentService-NoCache")
      .to[TabulaAssessmentServiceImpl]

    bind[TabulaStudentInformationService]
      .annotatedWithName("TabulaStudentInformationService-NoCache")
      .to[TabulaStudentInformationServiceImpl]

    bind[TabulaDepartmentService]
      .annotatedWithName("TabulaDepartmentService-NoCache")
      .to[TabulaDepartmentServiceImpl]

    // Default instance uses global Random with unpredictable seed. Override for tests where appropriate.
    bind[DataGeneration].toInstance(new DataGeneration(Random))
  }
}
