package controllers.sysadmin

import java.time.OffsetDateTime

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import org.quartz.Trigger.TriggerState
import org.quartz._
import play.api.i18n.Messages
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import services.SecurityService
import uk.ac.warwick.util.mywarwick.SendMyWarwickActivityJob

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object MyWarwickQueueController {
  case class QueuedMyWarwickActivity(
    key: TriggerKey,
    instanceBaseUrl: String,
    request: JsValue,
    alert: Boolean,
    transient: Boolean,
    created: OffsetDateTime,
    state: TriggerState
  )

  def queue(scheduler: Scheduler): Seq[QueuedMyWarwickActivity] =
    scheduler.getTriggersOfJob(SendMyWarwickActivityJob.JOB_KEY).asScala.toSeq.map { trigger =>
      val data = trigger.getJobDataMap

      QueuedMyWarwickActivity(
        key = trigger.getKey,
        instanceBaseUrl = data.getString(SendMyWarwickActivityJob.INSTANCE_BASE_URL_DATA_KEY),
        request = Json.parse(data.getString(SendMyWarwickActivityJob.REQUEST_BODY_JOB_DATA_KEY)),
        alert = data.getBooleanValueFromString(SendMyWarwickActivityJob.IS_NOTIFICATION_JOB_DATA_KEY),
        transient = data.getBooleanValueFromString(SendMyWarwickActivityJob.IS_TRANSIENT_JOB_DATA_KEY),
        created = OffsetDateTime.parse(data.getString(SendMyWarwickActivityJob.CREATED_DATETIME_ISO8601_DATA_KEY)),
        state = scheduler.getTriggerState(trigger.getKey)
      )
    }.sortBy { a => (a.created, a.instanceBaseUrl) }
}

@Singleton
class MyWarwickQueueController @Inject()(
  scheduler: Scheduler,
  securityService: SecurityService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import MyWarwickQueueController._
  import securityService._

  def queued(): Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.mywarwick.queue(queue(scheduler)))
  }

  def reschedule(triggerName: String, triggerGroup: String): Action[AnyContent] = RequireSysadmin { implicit request =>
    val key = new TriggerKey(triggerName, triggerGroup)
    scheduler.getTriggerState(key) match {
      case TriggerState.ERROR =>
        val trigger = scheduler.getTrigger(key)

        scheduler.rescheduleJob(
          key,
          TriggerBuilder.newTrigger()
            .withIdentity(key)
            .startNow()
            .usingJobData(trigger.getJobDataMap)
            .build()
        )

        Redirect(controllers.sysadmin.routes.MyWarwickQueueController.queued())
          .flashing("success" -> Messages("flash.mywarwick.queued"))

      case _ => Redirect(controllers.sysadmin.routes.MyWarwickQueueController.queued())
    }
  }

}
