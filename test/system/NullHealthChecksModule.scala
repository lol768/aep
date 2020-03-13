package system

import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import uk.ac.warwick.util.service.ServiceHealthcheckProvider

/**
  * Binds an empty set of healthchecks. We have to bind something if we want to use
  * any controllers because they are injected into HealthCheckController.
  */
class NullHealthChecksModule extends ScalaModule {

  override def configure(): Unit = {
    ScalaMultibinder.newSetBinder[ServiceHealthcheckProvider](binder)
  }

}
