package controllers.invigilation

import java.time.OffsetDateTime
import java.util.UUID

import controllers.{BaseController, RequestContext}
import AnnouncementAndQueriesController.{AnnouncementData, form}
import domain.{Announcement, Assessment}
import javax.inject.Inject
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.messaging.MessageService
import services.tabula.TabulaDepartmentService
import services.{AnnouncementService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.TimingContext
import warwick.sso.{AuthenticatedRequest, UniversityID, UserLookupService}

import scala.concurrent.ExecutionContext
object AnnouncementAndQueriesController {

  case class AnnouncementData(
    message: String
  )

  val form = Form(mapping(
    "message" -> nonEmptyText
  )(AnnouncementData.apply)(AnnouncementData.unapply))
}

class AnnouncementAndQueriesController @Inject()(
  security: SecurityService,
  userLookup: UserLookupService,
  studentAssessmentService: StudentAssessmentService,
  tabulaDepartmentService: TabulaDepartmentService,
  messageService: MessageService,
  announcementService: AnnouncementService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def view(assessmentId: UUID, universityId: UniversityID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      messageService.findByStudentAssessment(assessmentId, universityId),
      announcementService.getByAssessmentId(assessmentId),
      studentAssessmentService.getMetadata(universityId, assessmentId),
    ).successMap {
      case (departments, queries, announcements, studentAssessmentMetadata) =>
        val announcementsAndQueries = (queries.map(_.asAnnouncementOrQuery) ++ announcements.map(_.asAnnouncementOrQuery)).sortBy(_.date)(Ordering[OffsetDateTime].reverse)
        val student = userLookup.getUsers(Seq(universityId)).getOrElse(Map.empty)
        Ok(views.html.invigilation.studentQueries(
          req.assessment,
          studentAssessmentMetadata,
          announcementsAndQueries,
          student,
          department = departments.find(_.code == req.assessment.departmentCode.string),
        ))
    }
  }

  def viewAll(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    render(assessmentId, req.assessment, form)
  }

  def render(assessmentId: UUID, assessment: Assessment, form: Form[AnnouncementData])(implicit req: AuthenticatedRequest[_]) =
    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      messageService.findByAssessment(assessmentId),
      announcementService.getByAssessmentId(assessmentId)
    ).successMap {
      case (departments, queries, announcements) =>
        val announcementsAndQueries = (queries.map(_.asAnnouncementOrQuery) ++ announcements.map(_.asAnnouncementOrQuery)).sortBy(_.date)(Ordering[OffsetDateTime].reverse)
        val students = userLookup.getUsers(queries.map(_.client)).getOrElse(Map.empty)
        Ok(views.html.invigilation.allQueries(
          assessment,
          announcementsAndQueries,
          students,
          department = departments.find(_.code == assessment.departmentCode.string),
          form,
        ))
    }



  def addAnnouncement(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    form.bindFromRequest.fold(
      errors => render(assessmentId, req.assessment, errors),
      data => {
        announcementService.save(Announcement(assessment = req.assessment.id, text = data.message)).map( _ =>
          Redirect(controllers.invigilation.routes.AnnouncementAndQueriesController.viewAll(assessmentId))
            .flashing("success" -> Messages("flash.assessment.announcement.created"))
        )
      }
    )
  }


}


