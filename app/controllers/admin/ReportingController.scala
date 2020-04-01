package controllers.admin

import java.util.UUID

import controllers.{BaseController, RequestContext}
import domain.{Assessment, StudentAssessmentMetadata}
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.{AssessmentService, ReportingService, SecurityService}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.sso.{AuthenticatedRequest, UniversityID, User, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ReportingController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  reportingService: ReportingService,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def index: Action[AnyContent] = RequireAdmin.async { implicit request =>
    ServiceResults.zip(
      reportingService.todayAssessments,
      reportingService.liveAssessments
    ).successMap { case (today, live) =>
      val notLive = today diff live
      Ok(views.html.admin.reporting.index(live, notLive))
    }
  }

  def assessment(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    ServiceResults.zip(
      assessmentService.get(id),
      reportingService.expectedSittings(id),
      reportingService.startedSittings(id),
      reportingService.notStartedSittings(id),
      reportingService.submittedSittings(id),
      reportingService.finalisedSittings(id)
    ).successMap { case (assessment, expected, started, notStarted, submitted, finalised) =>
      val sittingMetadata = SittingMetadata(assessment, expected, started, notStarted, submitted, finalised)
      Ok(views.html.admin.reporting.assessment(sittingMetadata))
    }
  }

  def showExpandedList(id: UUID, title: String, getSittings: Future[ServiceResult[Seq[StudentAssessmentMetadata]]])(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] = {
    ServiceResults.zip(
      assessmentService.get(id),
      getSittings,
    ).successMap { case (assessment, sittings) =>
      userLookupService.getUsers(sittings.map(_.studentId)).map { userMap =>
        val sorted = sittings.sortBy(md => userMap.get(md.studentId).flatMap(_.name.last).getOrElse(""))
        Ok(views.html.admin.reporting.expandedList(assessment, sorted, userMap, title))
      }.getOrElse {
        Ok(views.html.admin.reporting.expandedList(assessment, sittings, Map.empty[UniversityID, User], title))
      }
    }
  }

  def expected(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students expected to take this assessment", reportingService.expectedSittings(id))
  }

  def started(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have started this assessment", reportingService.startedSittings(id))
  }

  def notStarted(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have not started this assessment", reportingService.notStartedSittings(id))
  }

  def submitted(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have submitted this assessment", reportingService.submittedSittings(id))
  }

  def finalised(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have finalised this assessment", reportingService.finalisedSittings(id))
  }
}

case class SittingMetadata(
  assessment: Assessment,
  expected: Seq[StudentAssessmentMetadata],
  started: Seq[StudentAssessmentMetadata],
  notStarted: Seq[StudentAssessmentMetadata],
  submitted: Seq[StudentAssessmentMetadata],
  finalised: Seq[StudentAssessmentMetadata]
)
