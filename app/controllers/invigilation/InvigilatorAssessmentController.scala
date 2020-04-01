package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.refiners.ActionRefiners
import services.{AssessmentService, ReportingService, SecurityService}

import scala.concurrent.ExecutionContext

class InvigilatorAssessmentController @Inject()(
  security: SecurityService,
  actionRefiners: ActionRefiners,
  assessmentService: AssessmentService,
  reportingService: ReportingService
)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def view(assessmentId: UUID): Action[AnyContent] = InvigilatorAsseessmentAction(assessmentId) { implicit req =>
    Ok(views.html.invigilation.assessment(req.assessment))
  }



}

