package system

import net.codingwell.scalaguice.ScalaModule
import org.quartz.Scheduler
import play.api.{Configuration, Environment}

class AppModule(environment: Environment, configuration: Configuration) extends ScalaModule {
  override def configure(): Unit = {
    // Enables Scheduler for injection. Scheduler.start() happens separately, in SchedulerConfigModule
    bind[Scheduler].toProvider[SchedulerProvider]
  }
}
