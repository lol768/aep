package services.healthcheck

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import com.google.inject.Inject
import services.AkkaClusterStateService
import warwick.core.Logging
import warwick.core.helpers.JavaTime

import scala.concurrent.duration._

class AkkaClusterSizeHealthCheck @Inject()(
  cluster: AkkaClusterStateService,
  system: ActorSystem,
) extends NumericHealthCheck[Int]("akka-cluster-size") with Logging {

  override def value: Int = cluster.state.members.size

  override def warning = 1

  override def critical = 0

  override def message: String = value match {
    case 0 => "This node has not joined a cluster"
    case 1 => "Only one member visible in the cluster"
    case n => s"$n members in cluster"
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
