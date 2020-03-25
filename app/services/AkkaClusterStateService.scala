package services

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle

@ImplementedBy(classOf[AkkaClusterStateServiceImpl])
trait AkkaClusterStateService {
  def selfAddress: Address
  def state: CurrentClusterState
}

class AkkaClusterStateServiceImpl @Inject() (
  akka: ActorSystem,
  life: ApplicationLifecycle
) extends AkkaClusterStateService {
  private[this] val cluster = Cluster(akka)

  override def selfAddress: Address = cluster.selfAddress
  override def state: CurrentClusterState = cluster.state
}
