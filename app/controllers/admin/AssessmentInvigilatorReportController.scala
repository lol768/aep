package controllers.admin

import java.time.format.DateTimeFormatter

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService}
import warwick.sso.UserLookupService

import scala.concurrent.ExecutionContext

@Singleton
class AssessmentInvigilatorReportController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  userLookupService: UserLookupService,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  val csvDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern(configuration.get[String]("app.csvDateTimeFormat"))

  def invigilatorsCsv(examProfileCode: String): Action[AnyContent] = RequireAdmin.async { implicit request =>
    assessmentService.listForExamProfileCodeWithStudentCount(examProfileCode).successMap { assessments =>
      val headerRow: Seq[String] = Seq("Module code", "Paper code", "Section", "Title", "Date/time", "Platform(s)", "Invigilators", "Students")
      val dataRows: Seq[Seq[String]] = assessments.map { case (assessment, students) =>
        Seq(
          assessment.moduleCode,
          assessment.paperCode,
          assessment.section.getOrElse(""),
          assessment.title,
          assessment.startTime.map(csvDateTimeFormat.format).getOrElse(""),
          assessment.platform.map(_.entryName).mkString(", "),
          userLookupService.getUsers(assessment.invigilators.toSeq).getOrElse(Map.empty).values.filter(_.isFound).map { user =>
            Seq(
              user.name.full.orElse(Some(user.usercode.string)),
              user.email.map(e => s"<$e>")
            ).flatten.mkString(" ")
          }.mkString(", "),
          students.toString
        )
      }

      Ok.chunked(csvSource(headerRow +: dataRows)).as("text/csv")
    }
  }

}
