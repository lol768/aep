package system

import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import play.api.{Configuration, Environment}
import services.healthcheck._
import uk.ac.warwick.util.core.scheduling.QuartzDAO
import uk.ac.warwick.util.service.ServiceHealthcheckProvider
import warwick.healthcheck.dao.SlickQuartzDAO

class HealthChecksModule(environment: Environment, configuration: Configuration) extends ScalaModule {
  override def configure(): Unit = {
    bind[HeathChecksStartup].asEagerSingleton()
    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val healthchecks = ScalaMultibinder.newSetBinder[ServiceHealthcheckProvider](binder)

    healthchecks.addBinding.to[UptimeHealthCheck]
    healthchecks.addBinding.to[EncryptedObjectStorageHealthCheck]
    healthchecks.addBinding.to[OutgoingEmailQueueHealthCheck]
    healthchecks.addBinding.to[OutgoingEmailDelayHealthCheck]
    healthchecks.addBinding.to[VirusScanServiceHealthCheck]
    healthchecks.addBinding.to[AkkaClusterSizeHealthCheck]
    healthchecks.addBinding.to[AkkaClusterUnreachableHealthCheck]

    healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck("default"))
    healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck("fileUploadsExecutionContext", "uploads.threads.fileUploadsExecutionContext"))
    configuration.get[Configuration]("threads").subKeys.toSeq.foreach { name =>
      healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck(name))
    }

    // For HealthCheckService
    bind[QuartzDAO].to[SlickQuartzDAO]
  }
}
