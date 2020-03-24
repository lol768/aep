package controllers

import java.util.UUID

import controllers.sysadmin.SysadminTestController.myWarwickForm
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Result}
import services.{SecurityService, StudentAssessmentService}
import warwick.fileuploads.UploadedFileControllerHelper
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages

import scala.concurrent.{ExecutionContext, Future}

object AssessmentController {
  case class FinishExamFormData(
    agreeDisclaimer: Boolean
  )

  val finishExamForm: Form[FinishExamFormData] = Form(mapping(
    "agreeDisclaimer" -> boolean.verifying(constraint=Predef.identity[Boolean])
  )(FinishExamFormData.apply)(FinishExamFormData.unapply))
}

@Singleton
class AssessmentController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit
  ec: ExecutionContext,
) extends BaseController {
  import security._

  def view(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId) { implicit request =>
    Ok(views.html.exam.index(request.studentAssessmentWithAssessment, AssessmentController.finishExamForm))
  }

  def start(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    studentAssessmentService.startAssessment(request.studentAssessmentWithAssessment.studentAssessment).successMap { _ =>
      Redirect(controllers.routes.AssessmentController.view(assessmentId))
    }
  }

  def finish(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId).async { implicit request =>
    AssessmentController.finishExamForm.bindFromRequest().fold(
      _ => Future.successful(Redirect(controllers.routes.AssessmentController.view(assessmentId))
        .flashing("error" -> Messages("flash.assessment.finish.must-check-box"))),
      _ => {
        studentAssessmentService.finishAssessment(request.studentAssessmentWithAssessment.studentAssessment).successMap { _ =>
          Redirect(controllers.routes.AssessmentController.view(assessmentId))
        }
      })
  }

  def downloadFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId).async { implicit request =>
    def notFound: Future[Result] =
      Future.successful(NotFound(views.html.errors.notFound()))

    request.studentAssessmentWithAssessment.assessment.brief.files
      .find(_.id == fileId)
      .fold(notFound)(uploadedFileControllerHelper.serveFile)
  }
}
