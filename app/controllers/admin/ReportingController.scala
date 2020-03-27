package controllers.admin

import java.util.UUID

import controllers.BaseController
import domain.StudentAssessmentMetadata
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, ReportingService, SecurityService}

import scala.concurrent.ExecutionContext


@Singleton
class ReportingController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  reportingService: ReportingService
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def index: Action[AnyContent] = RequireAdmin.async { implicit request =>
    reportingService.todayAssessments.successFlatMap(today => {
      reportingService.liveAssessments.successMap(live => {
        val notLive = today diff live
        Ok(views.html.admin.reporting.index(live, notLive))
      })
    })
  }

  def assessment(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    assessmentService.get(id).successFlatMap(assessment => {
      reportingService.expectedSittings(id).successFlatMap(expected => {
        reportingService.startedSittings(id).successFlatMap(started => {
          reportingService.submittedSittings(id).successFlatMap(submitted => {
            reportingService.finalisedSittings(id).successMap(finalised => {
              val sittingMetadata = SittingMetadata(expected, started, submitted, finalised)
              Ok(views.html.admin.reporting.assessment(assessment, sittingMetadata))
            })
          })
        })
      })
    })
  }
}

case class SittingMetadata(
  expected: Seq[StudentAssessmentMetadata],
  started: Seq[StudentAssessmentMetadata],
  submitted: Seq[StudentAssessmentMetadata],
  finalised: Seq[StudentAssessmentMetadata]
)
