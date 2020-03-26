package controllers

import java.util.UUID
import CreateAssessmentAnnouncementController._
import domain.Announcement
import play.api.data.Forms._
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.{AnnouncementService, AssessmentService, SecurityService}
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages
import warwick.sso.AuthenticatedRequest
import play.api.mvc.Result
import scala.concurrent.{ExecutionContext, Future}

object CreateAssessmentAnnouncementController {

  case class AssessmentAnnouncementData(
    assessmentId: UUID,
    message: String
  )

  val form = Form(mapping(
    "assessmentId" -> uuid,
    "message" -> nonEmptyText
  )(AssessmentAnnouncementData.apply)(AssessmentAnnouncementData.unapply))
}

@Singleton
class CreateAssessmentAnnouncementController @Inject()(
 security: SecurityService,
 assessmentService: AssessmentService,
 announcementService: AnnouncementService,
)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def index: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    renderForm(form)
  }

  def add: Action[AnyContent] = RequireSysadmin.async { implicit request => {

    form.bindFromRequest.fold(
      errors => renderForm(errors),
      data => {
        assessmentService.getByIdForInvigilator(data.assessmentId, List(currentUser().usercode)).successFlatMap { assessment =>
          announcementService.save(Announcement(assessment = assessment.id, text = data.message)).map( _ =>
            Redirect(controllers.routes.CreateAssessmentAnnouncementController.index)
              .flashing("success" -> Messages("flash.assessment.announcement.created"))
          )
        }
      }
    )
  }}


  private def renderForm(form: Form[AssessmentAnnouncementData])(implicit req: AuthenticatedRequest[_]): Future[Result] = {
    assessmentService.listForInvigilator(List(currentUser().usercode)).successMap{ assessments =>
      Ok(views.html.assessment.announcement(assessments, form))
    }
  }
}


