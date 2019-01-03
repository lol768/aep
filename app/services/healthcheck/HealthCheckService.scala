package services.healthcheck

import java.time.LocalDateTime

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import system.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object HealthCheckService {
  val frequency: FiniteDuration = 20.seconds
  val timeout: FiniteDuration = 5.seconds
}

@Singleton
class HealthCheckService @Inject()(system: ActorSystem) extends Logging {

  import HealthCheckService._

  var healthCheckLastRunAt: LocalDateTime = _

  def runNow(): Unit = {
    healthCheckLastRunAt = LocalDateTime.now()
  }

  runNow()
  system.scheduler.schedule(frequency, frequency)(runNow())

}