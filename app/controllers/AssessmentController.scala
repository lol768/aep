package controllers

import java.util.UUID

import domain.StudentAssessment
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.refiners.StudentAssessmentSpecificRequest
import services.{AnnouncementService, SecurityService, StudentAssessmentService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile

import scala.concurrent.{ExecutionContext, Future}

object AssessmentController {

  case class AuthorshipDeclarationFormData(
    agreeAuthorship: Boolean
  )

  case class ReasonableAdjustmentsDeclarationFormData(
    reasonableAdjustments: String
  ) {
    def hasDeclaredRA: Boolean =
      reasonableAdjustments == "hasRA"
  }

  case class UploadFilesFormData(
    xhr: Boolean
  )

  case class FinishExamFormData(
    agreeDisclaimer: Boolean
  )

  val authorshipDeclarationForm: Form[AuthorshipDeclarationFormData] = Form(mapping(
    "agreeAuthorship" -> checked("flash.assessment.declaration.must-check-box")
  )(AuthorshipDeclarationFormData.apply)(AuthorshipDeclarationFormData.unapply))

  val reasonableAdjustmentsDeclarationForm: Form[ReasonableAdjustmentsDeclarationFormData] = Form(mapping(
    "reasonableAdjustments" -> text.verifying(error="flash.assessment.declaration.must-choose-radio", constraint = Seq("hasRA", "hasNoRA").contains(_))
  )(ReasonableAdjustmentsDeclarationFormData.apply)(ReasonableAdjustmentsDeclarationFormData.unapply))

  val attachFilesToAssessmentForm: Form[UploadFilesFormData] = Form(mapping(
    "xhr" -> boolean
  )(UploadFilesFormData.apply)(UploadFilesFormData.unapply))

  val finishExamForm: Form[FinishExamFormData] = Form(mapping(
    "agreeDisclaimer" -> checked("flash.assessment.finish.must-check-box")
  )(FinishExamFormData.apply)(FinishExamFormData.unapply))
}

@Singleton
class AssessmentController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  uploadedFileService: UploadedFileService,
  announcementService: AnnouncementService,
)(implicit
  ec: ExecutionContext,
) extends BaseController {

  import security._

  private val redirectToAssessment = (id: UUID) => Redirect(controllers.routes.AssessmentController.view(id))

  private def doStart(studentAssessment: StudentAssessment)(implicit request: StudentAssessmentSpecificRequest[AnyContent]): Future[Result] = {
    studentAssessmentService.getOrDefaultDeclarations(studentAssessment.id).successFlatMap { declarations =>
      if (declarations.acceptable) {
        studentAssessmentService.startAssessment(request.sitting.studentAssessment).successMap { _ =>
          redirectToAssessment(studentAssessment.assessmentId).flashing("success" -> Messages("flash.assessment.started"))
        }
      } else if (!declarations.acceptsAuthorship) {
        Future.successful(Ok(views.html.exam.authorshipDeclaration(studentAssessment.assessmentId, AssessmentController.authorshipDeclarationForm)))
      } else if (!declarations.completedRA) {
        Future.successful(Ok(views.html.exam.reasonableAdjustmentsDeclaration(studentAssessment.assessmentId, AssessmentController.reasonableAdjustmentsDeclarationForm)))
      } else {
        Future.failed(throw new IllegalStateException("Unexpected declarations state"))
      }
    }
  }


  def view(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    announcementService.getByAssessmentId(assessmentId).successMap(announcements =>
      Ok(views.html.exam.index(request.sitting, AssessmentController.finishExamForm, uploadedFileControllerHelper.supportedMimeTypes, announcements))
    )
  }

  def authorshipDeclaration(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    AssessmentController.authorshipDeclarationForm.bindFromRequest().fold(
      form => Future.successful(BadRequest(views.html.exam.authorshipDeclaration(assessmentId, form))),
      formData => {
        studentAssessmentService.upsert(request.sitting.declarations.copy(acceptsAuthorship = formData.agreeAuthorship)).successFlatMap { _ =>
          doStart(request.sitting.studentAssessment)
        }
      }
    )
  }

  def reasonableAdjustmentsDeclaration(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    AssessmentController.reasonableAdjustmentsDeclarationForm.bindFromRequest().fold(
      form => Future.successful(BadRequest(views.html.exam.reasonableAdjustmentsDeclaration(assessmentId, form))),
      formData => {
        studentAssessmentService.upsert(request.sitting.declarations.copy(selfDeclaredRA = formData.hasDeclaredRA, completedRA = true)).successFlatMap { _ =>
          doStart(request.sitting.studentAssessment)
        }
      }
    )
  }

  def start(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    doStart(request.sitting.studentAssessment)
  }

  def finish(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId).async { implicit request =>
    AssessmentController.finishExamForm.bindFromRequest().fold(
      form => announcementService.getByAssessmentId(assessmentId).successMap(announcements =>
        BadRequest(views.html.exam.index(request.sitting, form, uploadedFileControllerHelper.supportedMimeTypes, announcements))
      ),
      _ => {
        if (request.sitting.finalised) {
          val flashMessage = "error" -> Messages("flash.assessment.alreadyFinalised")
          Future.successful(redirectToAssessment(assessmentId).flashing(flashMessage))
        } else if (!request.sitting.canModify()) {
          val flashMessage = "error" -> Messages("flash.assessment.lastFinaliseTimePassed")
          Future.successful(redirectToAssessment(assessmentId).flashing(flashMessage))
        } else {
          studentAssessmentService.finishAssessment(request.sitting.studentAssessment).successMap { _ =>
            redirectToAssessment(assessmentId)
          }
        }
      }
    )
  }

  def uploadFiles(assessmentId: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = StudentAssessmentInProgressAction(assessmentId)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    val files = request.body.files.map(_.ref)
    AssessmentController.attachFilesToAssessmentForm.bindFromRequest().fold(
      _ => announcementService.getByAssessmentId(assessmentId).successMap(announcements =>
        BadRequest(views.html.exam.index(request.sitting, AssessmentController.finishExamForm, uploadedFileControllerHelper.supportedMimeTypes, announcements))
      ),
      form => {
        val existingUploadedFiles = request.sitting.studentAssessment.uploadedFiles
        val intersection = existingUploadedFiles.map(uf => uf.fileName.toLowerCase).intersect(files.map(f => f.metadata.fileName.toLowerCase))
        if (files.isEmpty) {
          val flashMessage = "error" -> Messages("error.assessment.fileMissing")
          Future.successful(redirectOrReturn200(assessmentId, form, flashMessage))
        } else if (intersection.nonEmpty) {
          val flashMessage = "error" -> Messages("flash.assessment.filesDuplicates", intersection.head)
          Future.successful(redirectOrReturn200(assessmentId, form, flashMessage))
        } else {
          studentAssessmentService.attachFilesToAssessment(request.sitting.studentAssessment, files.map(f => (f.in, f.metadata))).successMap { _ =>
            val flashMessage = "success" -> Messages("flash.assessment.filesUploaded")
            redirectOrReturn200(assessmentId, form, flashMessage)
          }
        }
      }
    )
  }

  private def redirectOrReturn200(assessmentId: UUID, form: AssessmentController.UploadFilesFormData, flashMessage: (String, String)) = {
    if (form.xhr) {
      Ok.flashing(flashMessage)
    } else {
      redirectToAssessment(assessmentId).flashing(flashMessage)
    }
  }

  def deleteFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId).async { implicit request =>
    studentAssessmentService.deleteAttachedFile(request.sitting.studentAssessment, fileId)
    Future.successful(redirectToAssessment(assessmentId).flashing("success" -> Messages("flash.assessment.fileDeleted")))
  }

  def downloadAttachment(assessmentId: UUID, fileId: UUID): Action[AnyContent] = StudentAssessmentIsStartedAction(assessmentId).async { implicit request =>
    def notFound: Future[Result] =
      Future.successful(NotFound(views.html.errors.notFound()))

    request.sitting.studentAssessment.uploadedFiles
      .find(_.id == fileId)
      .fold(notFound)(uploadedFileControllerHelper.serveFile)
  }

  def downloadFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = StudentAssessmentIsStartedAction(assessmentId).async { implicit request =>
    def notFound: Future[Result] =
      Future.successful(NotFound(views.html.errors.notFound()))

    request.sitting.assessment.brief.files
      .find(_.id == fileId)
      .fold(notFound)(uploadedFileControllerHelper.serveFile)
  }
}
