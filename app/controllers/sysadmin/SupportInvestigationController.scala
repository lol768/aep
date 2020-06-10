package controllers.sysadmin

import java.io.ByteArrayOutputStream

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.SecurityService
import services.elasticsearch.SupportInvestigationService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SupportInvestigationController @Inject()(
  sis: SupportInvestigationService,
  security: SecurityService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def generateSpreadsheet(assessment: String, uniId: String): Action[AnyContent] = RequireSysadmin.async { implicit req =>
    if (assessment.isEmpty || uniId.isEmpty)
      Future(BadRequest)
    else sis.produceWorkbook(assessment, uniId, req.actualUser.get).successMap(wb => {
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
}
