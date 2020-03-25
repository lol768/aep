package controllers

import java.util.UUID

import domain.UploadedFileOwner
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.{SecurityService, StudentAssessmentService, UploadedFileService}
import warwick.core.helpers.ServiceResults
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile

import scala.concurrent.{ExecutionContext, Future}

object AssessmentController {

  case class FinishExamFormData(
    agreeDisclaimer: Boolean
  )

  val attachFilesToAssessmentForm: Form[UploadFilesFormData] = Form(mapping(
    "xhr" -> boolean
  )(UploadFilesFormData.apply)(UploadFilesFormData.unapply))

  val finishExamForm: Form[FinishExamFormData] = Form(mapping(
    "agreeDisclaimer" -> boolean.verifying(error = "flash.assessment.finish.must-check-box", constraint = Predef.identity[Boolean])
  )(FinishExamFormData.apply)(FinishExamFormData.unapply))

  case class UploadFilesFormData(
    xhr: Boolean
  )

}

@Singleton
class AssessmentController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  uploadedFileService: UploadedFileService
)(implicit
  ec: ExecutionContext,
) extends BaseController {

  import security._

  private val redirectToAssessment = (id: UUID) => Redirect(controllers.routes.AssessmentController.view(id))

  def view(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId) { implicit request =>
    Ok(views.html.exam.index(request.studentAssessmentWithAssessment, AssessmentController.finishExamForm, uploadedFileControllerHelper.supportedMimeTypes))
  }

  def start(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    studentAssessmentService.startAssessment(request.studentAssessmentWithAssessment.studentAssessment).successMap { _ =>
      redirectToAssessment(assessmentId).flashing("success" -> Messages("flash.assessment.started"))
    }
  }

  def finish(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId).async { implicit request =>
    AssessmentController.finishExamForm.bindFromRequest().fold(
      form => Future.successful(BadRequest(views.html.exam.index(request.studentAssessmentWithAssessment, form, uploadedFileControllerHelper.supportedMimeTypes))),
      _ => {
        studentAssessmentService.finishAssessment(request.studentAssessmentWithAssessment.studentAssessment).successMap { _ =>
          redirectToAssessment(assessmentId)
        }
      })
  }

  def uploadFiles(assessmentId: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = StudentAssessmentInProgressAction(assessmentId)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    val files = request.body.files.map(_.ref)
    AssessmentController.attachFilesToAssessmentForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest(views.html.exam.index(request.studentAssessmentWithAssessment, AssessmentController.finishExamForm, uploadedFileControllerHelper.supportedMimeTypes))),
      form =>
        studentAssessmentService.attachFilesToAssessment(request.studentAssessmentWithAssessment.studentAssessment, files.map(f => (f.in, f.metadata))).successMap { _ =>
          val flashMessage = "success" -> Messages("flash.assessment.filesUploaded")
          if (form.xhr) {
            Ok.flashing(flashMessage)
          } else {
            redirectToAssessment(assessmentId).flashing(flashMessage)
          }
        }
    )
  }

  def downloadAttachment(assessmentId: UUID, fileId: UUID): Action[AnyContent] = StudentAssessmentIsStartedAction(assessmentId).async { implicit request =>
    def notFound: Future[Result] =
      Future.successful(NotFound(views.html.errors.notFound()))

    request.studentAssessmentWithAssessment.studentAssessment.uploadedFiles
      .find(_.id == fileId)
      .fold(notFound)(uploadedFileControllerHelper.serveFile)
  }

  def downloadFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId).async { implicit request =>
    def notFound: Future[Result] =
      Future.successful(NotFound(views.html.errors.notFound()))

    request.studentAssessmentWithAssessment.assessment.brief.files
      .find(_.id == fileId)
      .fold(notFound)(uploadedFileControllerHelper.serveFile)
  }
}
