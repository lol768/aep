package system

import java.util.concurrent.TimeoutException

import javax.inject.Inject
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.pattern.after
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import warwick.core.Logging

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.Try

/**
  * Listens for app shutdown and leaves the cluster cleanly.
  */
class ClusterLifecycle  @Inject()(
  config: Configuration,
  lifecycle: ApplicationLifecycle,
  akka: ActorSystem
) extends Logging {

  if (config.getOptional[String]("akka.actor.provider").contains("akka.cluster.ClusterActorRefProvider")) {

    val cluster = Cluster(akka)
    val shutdownWait: FiniteDuration = 30.seconds

    import akka.dispatcher

    lifecycle.addStopHook(() => {
      logger.info("Leaving cluster because app is shutting down")
      val p = Promise[Unit]

      if (cluster.state.members.isEmpty) {
        // This mainly happens in functional tests - node might not have had time to join itself.
        logger.info("Cluster is empty, okay to continue shutdown")
        Future.successful[Unit](())
      } else {
        cluster.leave(cluster.selfAddress)
        cluster.registerOnMemberRemoved {
          logger.info("Left cluster cleanly, okay to continue shutdown")
          p.complete(Try(()))
        }

        // Give up waiting after a decent amount of time so there's no chance of us
        // blocking shutdown forever.
        Future.firstCompletedOf(Seq(
          p.future,
          after(shutdownWait, akka.scheduler)(Future.failed(new TimeoutException(s"Waited ${shutdownWait} to leave cluster, timed out")))
        ))
      }
    })

  } else {
    logger.info("ClusterActorRefProvider not in use, so I'll do nothing")
  }
}
