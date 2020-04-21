package controllers.invigilation

import java.time.OffsetDateTime
import java.util.UUID

import controllers.BaseController
import controllers.invigilation.AnnouncementAndQueriesController.{AnnouncementData, form}
import domain.{Announcement, Assessment}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService.{GetMultipleStudentInformationOptions, GetStudentInformationOptions}
import services.tabula.{TabulaDepartmentService, TabulaStudentInformationService}
import services.{AnnouncementService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.sso.{AuthenticatedRequest, UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}
object AnnouncementAndQueriesController {

  case class AnnouncementData(
    message: String
  )

  val form: Form[AnnouncementData] = Form(mapping(
    "message" -> nonEmptyText
  )(AnnouncementData.apply)(AnnouncementData.unapply))
}

@Singleton
class AnnouncementAndQueriesController @Inject()(
  security: SecurityService,
  studentInformationService: TabulaStudentInformationService,
  studentAssessmentService: StudentAssessmentService,
  tabulaDepartmentService: TabulaDepartmentService,
  messageService: MessageService,
  announcementService: AnnouncementService,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def view(assessmentId: UUID, universityId: UniversityID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      messageService.findByStudentAssessment(assessmentId, universityId),
      announcementService.getByAssessmentId(assessmentId),
      studentAssessmentService.get(universityId, assessmentId),
      studentInformationService.getStudentInformation(GetStudentInformationOptions(universityId)),
      Future.successful(ServiceResults.fromTry(userLookupService.getUsers(req.assessment.invigilators.toSeq))),
    ).successMap {
      case (departments, queries, announcements, studentAssessment, profile, invigilators) =>
        Ok(views.html.invigilation.studentQueries(
          req.assessment,
          studentAssessment,
          announcements.sortBy(_.created)(Ordering[OffsetDateTime].reverse),
          queries.sortBy(_.created)(Ordering[OffsetDateTime].reverse),
          Map(universityId -> profile),
          invigilators,
          department = departments.find(_.code == req.assessment.departmentCode.string),
        ))
    }
  }

  def viewAll(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    render(assessmentId, req.assessment, form)
  }

  def render(assessmentId: UUID, assessment: Assessment, form: Form[AnnouncementData])(implicit req: AuthenticatedRequest[_]): Future[Result] =
    ServiceResults.zip(
      tabulaDepartmentService.getDepartments,
      messageService.findByAssessment(assessmentId),
      announcementService.getByAssessmentId(assessmentId),
      Future.successful(ServiceResults.fromTry(userLookupService.getUsers(assessment.invigilators.toSeq))),
    ).successFlatMap {
      case (departments, queries, announcements, invigilators) =>
        studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = queries.map(_.student))).successMap { students =>
          Ok(views.html.invigilation.allQueries(
            assessment,
            announcements.sortBy(_.created)(Ordering[OffsetDateTime].reverse),
            queries.sortBy(_.created)(Ordering[OffsetDateTime].reverse),
            students,
            department = departments.find(_.code == assessment.departmentCode.string),
            form,
            invigilators,
          ))
        }
    }



  def addAnnouncement(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    form.bindFromRequest.fold(
      errors => render(assessmentId, req.assessment, errors),
      data => {
        announcementService.save(Announcement(assessment = req.assessment.id, sender = Some(currentUser().usercode), text = data.message)).map( _ =>
          Redirect(controllers.invigilation.routes.AnnouncementAndQueriesController.viewAll(assessmentId))
            .flashing("success" -> Messages("flash.assessment.announcement.created"))
        )
      }
    )
  }


}


