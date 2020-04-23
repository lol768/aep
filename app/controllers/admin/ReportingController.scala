package controllers.admin

import java.util.UUID

import controllers.BaseController
import domain.messaging.MessageSender
import domain.{SittingMetadata, StudentAssessment, tabula}
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService._
import services.{AnnouncementService, AssessmentClientNetworkActivityService, AssessmentService, ReportingService, SecurityService}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.sso.{AuthenticatedRequest, UniversityID}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ReportingController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  reportingService: ReportingService,
  studentInformationService: TabulaStudentInformationService,
  messageService: MessageService,
  networkActivityService: AssessmentClientNetworkActivityService,
  announcementService: AnnouncementService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def index: Action[AnyContent] = RequireAdmin.async { implicit request =>
    ServiceResults.zip(
      reportingService.last48HrsAssessments,
      reportingService.startedAndSubmittableAssessments,
    ).successMap { case (today, startedAndSubmittable) =>
      val notLive = today diff startedAndSubmittable
      Ok(views.html.admin.reporting.index(startedAndSubmittable, notLive))
    }
  }

  def assessment(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    ServiceResults.zip(
      assessmentService.get(id),
      reportingService.assessmentReport(id),
    ).successMap { case (assessment, report) =>
      Ok(views.html.admin.reporting.assessment(assessment, report))
    }
  }

  def showExpandedList(id: UUID, title: String, route: String, getSittings: Future[ServiceResult[Seq[StudentAssessment]]])(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] = {
    ServiceResults.zip(
      assessmentService.get(id),
      getSittings,
      messageService.findByAssessment(id),
      announcementService.getByAssessmentId(id)
    ).successFlatMap { case (assessment, sittings, queries, announcements) =>
      studentInformationService
        .getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = sittings.map(_.studentId)))
        .successMap { profiles =>
          val sorted = sittings
            .sortBy(studentAssessmentOrdering(profiles))
            .map(SittingMetadata(_, assessment.asAssessmentMetadata))
          Ok(views.html.admin.reporting.expandedList(assessment, sorted, profiles, title, route, queries.map(_.client).distinct, queries.count(_.sender == MessageSender.Client), announcements.length))
        }
    }
  }

  def showStudentAssessmentInfoTable(getSittings: Future[ServiceResult[Seq[StudentAssessment]]], assessmentId: UUID, sortByHeader: Option[String] = None)(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] = {
    ServiceResults.zip(
      assessmentService.get(assessmentId),
      getSittings,
      messageService.findByAssessment(assessmentId),
      announcementService.getByAssessmentId(assessmentId),
    ).successFlatMap {
      case (assessment, sittings, queries, announcements) =>
        ServiceResults.zip(
          studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = sittings.map(_.studentId))),
          networkActivityService.getLatestActivityFor(sittings.map(_.id))
        ).successMap { case (profiles, latestActivities) =>
            val sorted = sittings
              .sortBy(studentAssessmentOrdering(profiles))
              .map(SittingMetadata(_, assessment.asAssessmentMetadata))
            Ok(views.html.tags.queriesAndStudents(
              sorted,
              assessment.platform,
              profiles,
              Some(queries.map(_.client).distinct),
              queries.count(_.sender == MessageSender.Client),
              announcements.length,
              latestActivities,
              sortByHeader,
              viewAllAnnouncementsAndQueries = Some(controllers.invigilation.routes.AnnouncementAndQueriesController.viewAll(assessment.id)),
              viewSingleAnnouncementsAndQueries = Some(sa => controllers.invigilation.routes.AnnouncementAndQueriesController.view(assessment.id, sa.studentId)),
            ))
          }
    }
  }

  private def studentAssessmentOrdering(profiles: Map[UniversityID, tabula.SitsProfile])(studentAssessment: StudentAssessment) = {
    val profile = profiles.get(studentAssessment.studentId)
    (profile.map(_.lastName), profile.map(_.firstName), studentAssessment.studentId.string)
  }

  def expected(id: UUID): Action[AnyContent] = RequireAdmin.async { implicit request =>
    showExpandedList(id, "Students expected to take this assessment", "expected", reportingService.expectedSittings(id))
  }

  def expectedTable(id: UUID): Action[AnyContent] = InvigilatorAssessmentAction(id).async { implicit request =>
    val sortByHeader = request.getQueryString("sort").flatMap(v => if(v.isEmpty) None else Some(v))
    showStudentAssessmentInfoTable(reportingService.expectedSittings(id), id, sortByHeader)
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
