package system

import com.google.inject.Injector
import javax.inject.{Inject, Singleton}
import org.quartz.spi.{JobFactory, TriggerFiredBundle}
import org.quartz.{Job, Scheduler}

@Singleton
class GuiceJobFactory @Inject()(injector: Injector) extends JobFactory {

  override def newJob(triggerFiredBundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    injector.getInstance(triggerFiredBundle.getJobDetail.getJobClass)

}
