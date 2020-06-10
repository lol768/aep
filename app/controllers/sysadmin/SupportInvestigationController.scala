package controllers.sysadmin

import java.io.ByteArrayOutputStream

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.mvc.{Action, AnyContent}
import services.SecurityService
import services.elasticsearch.SupportInvestigationService

import scala.concurrent.{ExecutionContext, Future}

object SupportInvestigationController {
  case class GenerateSpreadsheetForm(uniId: String, assessment: String)

  val generateSpreadsheetForm: Form[GenerateSpreadsheetForm] = Form(mapping(
    "uniId" -> nonEmptyText,
    "assessment" -> nonEmptyText,
    )(GenerateSpreadsheetForm.apply)(GenerateSpreadsheetForm.unapply))

}

@Singleton
class SupportInvestigationController @Inject()(
  sis: SupportInvestigationService,
  security: SecurityService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def form(): Action[AnyContent] = RequireSysadmin { implicit req =>
    Ok(views.html.sysadmin.sis(SupportInvestigationController.generateSpreadsheetForm))
  }

  def generateSpreadsheet(): Action[AnyContent] = RequireSysadmin.async { implicit req =>
    SupportInvestigationController.generateSpreadsheetForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest),
      data => {
        sis.produceWorkbook(data.assessment, data.uniId, req.actualUser.get).successMap(wb => {
          val responseStream = new ByteArrayOutputStream()
          wb.write(responseStream)
          Ok(
            responseStream.toByteArray
          ).as("application/vnd.ms-excel").withHeaders(
            "Content-Disposition" ->
              (s"attachment; filename="+wb.getXSSFWorkbook.getProperties.getCoreProperties.getTitle+".xls")
          )
        })
      }
    )
  }
}
