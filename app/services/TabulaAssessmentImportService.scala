package services

import java.time.Duration
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment.State.Imported
import domain.Assessment.{AssessmentType, Brief, Platform}
import domain.tabula.AssessmentComponent
import domain.{Assessment, DepartmentCode}
import javax.inject.{Inject, Singleton}
import services.tabula.TabulaAssessmentService.GetAssessmentsOptions
import services.tabula.{TabulaAssessmentService, TabulaDepartmentService}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


case class DepartmentWithAssessments(
  departmentCode: String,
  assessment: Seq[Assessment],
  errorProcessingDepartment: Boolean
)

case class AssessmentImportResult(departmentWithAssessments: Seq[DepartmentWithAssessments]) {
  def error: Boolean = departmentWithAssessments.exists(_.errorProcessingDepartment)
}

@ImplementedBy(classOf[TabulaAssessmentImportServiceImpl])
trait TabulaAssessmentImportService {
  def importAssessments(): Future[ServiceResult[AssessmentImportResult]]

}


@Singleton
class TabulaAssessmentImportServiceImpl @Inject()(
  tabulaDepartmentService: TabulaDepartmentService,
  tabulaAssessmentService: TabulaAssessmentService,
  assessmentService: AssessmentService

)(implicit ec: ExecutionContext) extends TabulaAssessmentImportService with Logging {
  implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()
  implicit val timingContext: TimingContext = TimingContext.none


  def importAssessments(): Future[ServiceResult[AssessmentImportResult]] = {
    tabulaDepartmentService.getDepartments().successMapTo { departments =>
      logger.info(s"Import started. Total departments to process: ${departments.size} ")
      val departmentWithAssessments = departments.flatMap { department =>
        logger.info(s"Processing department ${department.code} ")
        Await.result(process(department.code), 30.seconds).toOption
      }
      AssessmentImportResult(departmentWithAssessments)
    }.recover {
      case e =>
        logger.error(s"Error processing departments API ${e.getMessage}")
        ServiceResults.exceptionToServiceResult(e)
    }
  }

  private def generateAssessment(ac: AssessmentComponent): Future[ServiceResult[Assessment]] = {
    val assessment = Assessment(
      id = UUID.fromString(ac.id),
      code = ac.examPaper.map(_.code).getOrElse(""),
      title = ac.examPaper.map(_.title.getOrElse("")).getOrElse(""), //TODO Fix this as this will be assessment title to be exposed via Tabula API. Time being set as paper title
      startTime = None, //TODO This would need to be set
      duration = Duration.ofHours(3), //TODO - This would be populated from API
      platform = Platform.OnlineExams,
      assessmentType = AssessmentType.OpenBook,
      brief = Brief(None, Nil, None),
      state = Imported,
      tabulaAssessmentId = Some(UUID.fromString(ac.id)), //for assessments created within app directly this will be blank.
      moduleCode = s"${ac.module.code}-${ac.cats}",
      departmentCode = DepartmentCode(ac.module.adminDepartment.code)
    )
    assessmentService.insert(assessment).successFlatMapTo { row => Future.successful(Right(row))
    }
  }

  private def process(departmentCode: String): Future[ServiceResult[DepartmentWithAssessments]] = {
    tabulaAssessmentService.getAssessments(GetAssessmentsOptions(departmentCode, true)).map(_.fold(
      error => {
        logger.error(s"Error processing assessment API")
        ServiceResults.success(DepartmentWithAssessments(departmentCode, Nil, true))
      },
      assessmentComponents => {
        try {
          val components = assessmentComponents.flatMap { assessmentComponent =>
            Await.result(generateAssessment(assessmentComponent), 10.seconds).toOption
          }
          ServiceResults.success(DepartmentWithAssessments(departmentCode, components, false))
        } catch {
          case e: Exception =>
            logger.error(s"Error processing assessments of department $departmentCode")
            // If there are db errors still need to proceed for other departments, just log department has failure
            ServiceResults.success(DepartmentWithAssessments(departmentCode, Nil, true))
        }
      }
    ))
  }

}


