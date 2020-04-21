package controllers.sysadmin

import java.util.UUID

import controllers.BaseController
import domain.OutgoingEmail
import javax.inject.{Inject, Singleton}
import org.quartz.Trigger.TriggerState
import org.quartz._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.job.SendOutgoingEmailJob
import services.{EmailService, SecurityService, TabulaAssessmentImportService}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

@Singleton
class TabulaAssessmentsImportsController @Inject()(
  tabulaAssessmentImportService: TabulaAssessmentImportService,
  scheduler: Scheduler,
  securityService: SecurityService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  private def getTriggerState: TriggerState =
    scheduler.getTriggerState(tabulaAssessmentImportService.getImportTriggerKey)

  def showForm(): Action[AnyContent] = RequireSysadmin { implicit request =>
    val triggerState = getTriggerState
    val isPaused = triggerState == TriggerState.PAUSED
    Ok(views.html.sysadmin.tabulaAssessmentImports(triggerState.name, isPaused))
  }

  def toggleTriggerState(): Action[AnyContent] = RequireSysadmin { implicit request =>
    val triggerState = getTriggerState
    val isPaused = triggerState == TriggerState.PAUSED
    if (isPaused) {
      tabulaAssessmentImportService.resumeImports()
      Redirect(controllers.sysadmin.routes.TabulaAssessmentsImportsController.showForm())
        .flashing("success" -> Messages("flash.tabulaAssessmentImports.resumed"))
    } else {
      tabulaAssessmentImportService.pauseImports()
      Redirect(controllers.sysadmin.routes.TabulaAssessmentsImportsController.showForm())
        .flashing("success" -> Messages("flash.tabulaAssessmentImports.paused"))
    }
  }

}
