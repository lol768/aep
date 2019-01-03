package system

import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import play.api.{Configuration, Environment}
import services.healthcheck._
import uk.ac.warwick.util.service.ServiceHealthcheckProvider

class AppModule(environment: Environment, configuration: Configuration) extends ScalaModule {
  override def configure(): Unit = {
    bind[AppStartup].asEagerSingleton()
    bindHealthChecks()
  }

  def bindHealthChecks(): Unit = {
    val healthchecks = ScalaMultibinder.newSetBinder[ServiceHealthcheckProvider](binder)
    healthchecks.addBinding.to[UptimeHealthCheck]

    healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck("default"))
    configuration.get[Configuration]("threads").subKeys.toSeq.foreach { name =>
      healthchecks.addBinding.toInstance(new ThreadPoolHealthCheck(name))
    }
  }
}
