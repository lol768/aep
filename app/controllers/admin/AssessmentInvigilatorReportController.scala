package controllers.admin

import java.time.format.DateTimeFormatter

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService}
import warwick.sso.UserLookupService

import scala.concurrent.ExecutionContext

@Singleton
class AssessmentInvigilatorReportController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  val csvDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

  def invigilatorsCsv(examProfileCode: String): Action[AnyContent] = RequireAdmin.async { implicit request =>
    assessmentService.listForExamProfileCode(examProfileCode).successMap { assessments =>
      val headerRow: Seq[String] = Seq("Module code", "Paper code", "Section", "Title", "Date/time", "Invigilators")
      val dataRows: Seq[Seq[String]] = assessments.map { assessment =>
        Seq(
          assessment.moduleCode,
          assessment.paperCode,
          assessment.section.getOrElse(""),
          assessment.title,
          assessment.startTime.map(csvDateTimeFormat.format).getOrElse(""),
          userLookupService.getUsers(assessment.invigilators.toSeq).getOrElse(Map.empty).values.filter(_.isFound).map { user =>
            Seq(
              user.name.full.orElse(Some(user.usercode.string)),
              user.email.map(e => s"<$e>")
            ).flatten.mkString(" ")
          }.mkString(", ")
        )
      }

      Ok.chunked(csvSource(headerRow +: dataRows)).as("text/csv")
    }
  }

}
