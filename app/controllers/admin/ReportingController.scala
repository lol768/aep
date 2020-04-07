package controllers.admin

import java.util.UUID

import controllers.{BaseController, RequestContext}
import domain.{Assessment, StudentAssessmentMetadata}
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
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
  studentInformationService: TabulaStudentInformationService,
  messageService: MessageService,
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
      reportingService.notStartedSittings(id),
      reportingService.submittedSittings(id),
      reportingService.finalisedSittings(id)
    ).successMap { case (assessment, expected, started, notStarted, submitted, finalised) =>
      val sittingMetadata = SittingMetadata(assessment, expected, started, notStarted, submitted, finalised)
      Ok(views.html.admin.reporting.assessment(sittingMetadata))
    }
  }

  def showExpandedList(id: UUID, title: String, route: String, getSittings: Future[ServiceResult[Seq[StudentAssessmentMetadata]]])(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] = {
    ServiceResults.zip(
      assessmentService.get(id),
      getSittings,
      messageService.findByAssessment(id)
    ).successFlatMap { case (assessment, sittings, queries) =>
      studentInformationService
        .getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = sittings.map(_.studentId)))
        .successMap { profiles =>
          val sorted = sittings.sortBy(md => (profiles.get(md.studentId).map(_.lastName), profiles.get(md.studentId).map(_.firstName), md.studentId.string))
          Ok(views.html.admin.reporting.expandedList(assessment, sorted, profiles, title, route, queries.map(_.client).distinct))
        }
    }
  }

  def showStudentAssessmentInfoTable(getSittings: Future[ServiceResult[Seq[StudentAssessmentMetadata]]], assessmentId: UUID)(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] = {
    ServiceResults.zip(
      getSittings,
      messageService.findByAssessment(assessmentId)
    ).successFlatMap {
      case (sittings, queries) =>
        studentInformationService
          .getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = sittings.map(_.studentId)))
          .successMap { profiles =>
            val sorted = sittings.sortBy(md => (profiles.get(md.studentId).map(_.lastName), profiles.get(md.studentId).map(_.firstName), md.studentId.string))
            Ok(views.html.tags.studentAssessmentInfo(sorted, profiles, Some(queries.map(_.client).distinct)))
          }
    }
  }

  def expected(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students expected to take this assessment", "expected", reportingService.expectedSittings(id))
  }

  def expectedTable(id: UUID): Action[AnyContent] = InvigilatorAssessmentAction(id).async { implicit request =>
    showStudentAssessmentInfoTable(reportingService.expectedSittings(id), id)
  }

  def started(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have started this assessment", "started", reportingService.startedSittings(id))
  }

  def startedTable(id: UUID): Action[AnyContent] = InvigilatorAssessmentAction(id).async { implicit request =>
    showStudentAssessmentInfoTable(reportingService.startedSittings(id), id)
  }

  def notStarted(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have not started this assessment", "notstarted", reportingService.notStartedSittings(id))
  }

  def notStartedTable(id: UUID): Action[AnyContent] = InvigilatorAssessmentAction(id).async { implicit request =>
    showStudentAssessmentInfoTable(reportingService.notStartedSittings(id), id)
  }

  def submitted(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have submitted this assessment", "submitted", reportingService.submittedSittings(id))
  }

  def submittedTable(id: UUID): Action[AnyContent] = InvigilatorAssessmentAction(id).async { implicit request =>
    showStudentAssessmentInfoTable(reportingService.submittedSittings(id), id)
  }

  def finalised(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students that have finalised this assessment", "finalised", reportingService.finalisedSittings(id))
  }

  def finalisedTable(id: UUID): Action[AnyContent] = InvigilatorAssessmentAction(id).async { implicit request =>
    showStudentAssessmentInfoTable(reportingService.finalisedSittings(id), id)
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
