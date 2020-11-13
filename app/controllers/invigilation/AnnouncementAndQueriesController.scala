package controllers.invigilation

import java.time.OffsetDateTime
import java.util.UUID

import controllers.{API, BaseController}
import controllers.invigilation.AnnouncementAndQueriesController.form
import domain.messaging.{MessageSave, MessageSender}
import domain.{Announcement, Assessment}
import javax.inject.{Inject, Singleton}
import play.api.data.Forms._
import play.api.data.{Form, Forms}
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService.{GetMultipleStudentInformationOptions, GetStudentInformationOptions}
import services.tabula.{TabulaDepartmentService, TabulaStudentInformationService}
import services.{AnnouncementService, SecurityService, StudentAssessmentService, TimingInfoService}
import system.Features
import warwick.core.helpers.ServiceResults
import warwick.sso.{AuthenticatedRequest, UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}
object AnnouncementAndQueriesController {

  val form: Form[String] = Form(Forms.single(
    "message" -> nonEmptyText
  ))
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
  features: Features,
  timingInfo: TimingInfoService,
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
          timingInfo = timingInfo
        ))
    }
  }

  def viewAll(assessmentId: UUID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    renderAll(assessmentId, req.assessment, form)
  }

  def renderAll(assessmentId: UUID, assessment: Assessment, form: Form[String])(implicit req: AuthenticatedRequest[_]): Future[Result] =
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
      errors => renderAll(assessmentId, req.assessment, errors),
      data => {
        announcementService.save(Announcement(assessment = req.assessment.id, sender = Some(currentUser().usercode), text = data)).map( _ =>
          Redirect(controllers.invigilation.routes.AnnouncementAndQueriesController.viewAll(assessmentId))
            .flashing("success" -> Messages("flash.assessment.announcement.created"))
        )
      }
    )
  }

  def addMessage(assessmentId: UUID, studentId: UniversityID): Action[AnyContent] = InvigilatorAssessmentAction(assessmentId).async { implicit req =>
    if (features.twoWayMessages) {
      form.bindFromRequest.fold(
        errors => render.async {
          case Accepts.Json() => Future.successful(API.badRequestJson(errors))
          case _ => renderAll(assessmentId, req.assessment, errors)
        },
        data => {
          messageService.send(MessageSave(data, MessageSender.Invigilator, Some(currentUser().usercode)), studentId, assessmentId).successMap { _ =>
            render {
              case Accepts.Json() => Ok(Json.toJson(API.Success(data = Json.obj())))
              case _ => Redirect(controllers.invigilation.routes.AnnouncementAndQueriesController.view(assessmentId, studentId))
                .flashing("success" -> Messages("flash.messages.sentToStudent"))
            }
          }
        }
      )
    } else {
      Future.successful(NotFound(views.html.errors.notFound()))
    }
  }


}


