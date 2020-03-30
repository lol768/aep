package controllers.admin

import controllers.{AssessmentController, BaseController}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services.{AssessmentService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object AssessmentImportController {
  val uploadSpreadsheetToImport: Form[UploadSpreadsheetData] = Form(mapping(
    "xhr" -> boolean
  )(UploadSpreadsheetData.apply)(UploadSpreadsheetData.unapply))


  case class UploadSpreadsheetData(
    xhr: Boolean
  )
}

@Singleton
class AssessmentImportController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  private val redirectHome = Redirect(controllers.admin.routes.AssessmentImportController.index())


  // might well change to have a different role in future (just for exams)
  def index: Action[AnyContent] = RequireAdmin { implicit request =>
    Ok(views.html.admin.spreadsheetImport.index(AssessmentImportController.uploadSpreadsheetToImport))
  }

  def processImport: Action[MultipartFormData[TemporaryFile]] = RequireAdmin(parse.multipartFormData).async { implicit request =>
    val files = request.body.files.map(_.ref)
    // NB: *Not* in object storage
    AssessmentController.attachFilesToAssessmentForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest(views.html.admin.spreadsheetImport.index(AssessmentImportController.uploadSpreadsheetToImport))),
      form => {
        val flashMessage = "success" -> Messages("flash.spreadsheetImport.processed", Random.between(1, 101))
        if (form.xhr) {
          Future.successful(Ok.flashing(flashMessage))
        } else {
          Future.successful(redirectHome.flashing(flashMessage))
        }
      }
    )
  }
}
