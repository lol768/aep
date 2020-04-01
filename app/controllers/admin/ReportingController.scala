package controllers.admin

import java.util.UUID

import controllers.BaseController
import domain.{Assessment, StudentAssessmentMetadata}
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, ReportingService, SecurityService}
import warwick.core.helpers.ServiceResults

import scala.concurrent.ExecutionContext


@Singleton
class ReportingController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  reportingService: ReportingService
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def index: Action[AnyContent] = RequireAdmin.async { implicit request =>
    ServiceResults.zip(
      reportingService.todayAssessments,
      reportingService.startedAndSubmittableAssessments,
    ).successMap { case (today, startedAndSubmittable) =>
      val notLive = today diff startedAndSubmittable
      Ok(views.html.admin.reporting.index(startedAndSubmittable, notLive))
    }
  }

  def assessment(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    ServiceResults.zip(
      assessmentService.get(id),
      reportingService.expectedSittings(id),
      reportingService.startedSittings(id),
      reportingService.submittedSittings(id),
      reportingService.finalisedSittings(id)
    ).successMap { case (assessment, expected, started, submitted, finalised) =>
      val sittingMetadata = SittingMetadata(assessment, expected, started, submitted, finalised)
      Ok(views.html.admin.reporting.assessment(sittingMetadata))
    }
  }
}

case class SittingMetadata(
  assessment: Assessment,
  expected: Seq[StudentAssessmentMetadata],
  started: Seq[StudentAssessmentMetadata],
  submitted: Seq[StudentAssessmentMetadata],
  finalised: Seq[StudentAssessmentMetadata]
)
