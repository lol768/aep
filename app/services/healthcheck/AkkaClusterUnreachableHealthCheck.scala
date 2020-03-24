package services.healthcheck

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import com.google.inject.Inject
import services.AkkaClusterStateService
import warwick.core.Logging
import warwick.core.helpers.JavaTime

import scala.concurrent.duration._

class AkkaClusterUnreachableHealthCheck @Inject()(
  cluster: AkkaClusterStateService,
  system: ActorSystem,
) extends NumericHealthCheck[Int]("akka-cluster-unreachable") with Logging {

  override def value: Int = cluster.state.unreachable.size

  override def warning = 1

  override def critical = 2

  override def message: String = value match {
    case 0 => "No members are unreachable"
    case n => s"$n members are unreachable"
  }

  override def testedAt: OffsetDateTime = JavaTime.offsetDateTime

  system.scheduler.scheduleAtFixedRate(0.seconds, interval = 5.seconds)(() => {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  })(system.dispatcher)

}
