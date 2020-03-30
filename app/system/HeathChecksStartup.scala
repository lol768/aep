package system

import javax.inject.{Inject, Provider}
import services.healthcheck.ThreadPoolHealthCheck
import uk.ac.warwick.util.service.ServiceHealthcheckProvider

/**
  * Does some startup tasks where they can't be done in other
  * constructors for whatever reason.
  */
class HeathChecksStartup @Inject()(
  healthchecks: Provider[Set[ServiceHealthcheckProvider]]
) {
  /**
    * Some healthchecks need initting but rely on field injection
    * so can't use its dependencies in its constructor.
    */
  healthchecks.get.foreach {
    case check: ThreadPoolHealthCheck => check.init()
    case _ =>
  }
}
