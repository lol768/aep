package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment
import domain.tabula.AssessmentComponent
import javax.inject.{Inject, Singleton}
import services.tabula.TabulaAssessmentService.GetAssessmentsOptions
import services.tabula.{TabulaAssessmentService, TabulaDepartmentService}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext

import scala.concurrent.{ExecutionContext, Future}

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
  def importAssessments()(implicit ctx: AuditLogContext): Future[ServiceResult[AssessmentImportResult]]
}

@Singleton
class TabulaAssessmentImportServiceImpl @Inject()(
  tabulaDepartmentService: TabulaDepartmentService,
  tabulaAssessmentService: TabulaAssessmentService,
  assessmentService: AssessmentService
)(implicit ec: ExecutionContext) extends TabulaAssessmentImportService with Logging {
  def importAssessments()(implicit ctx: AuditLogContext): Future[ServiceResult[AssessmentImportResult]] =
    tabulaDepartmentService.getDepartments().successFlatMapTo { departments =>
      logger.info(s"Import started. Total departments to process: ${departments.size}")

      ServiceResults.futureSequence(departments.map(d => process(d.code)))
        .successMapTo { departmentWithAssessments =>
          logger.info(s"Processed total departments: ${departmentWithAssessments.filterNot(_.errorProcessingDepartment).size}")
          AssessmentImportResult(departmentWithAssessments)
        }
    }

  private def process(departmentCode: String)(implicit ctx: AuditLogContext): Future[ServiceResult[DepartmentWithAssessments]] = {
    logger.info(s"Processing department $departmentCode")

    tabulaAssessmentService.getAssessments(GetAssessmentsOptions(departmentCode, withExamPapersOnly = true))
      .successFlatMapTo { assessmentComponents =>
        ServiceResults.futureSequence(assessmentComponents.map(generateAssessment))
          .successMapTo { components =>
            DepartmentWithAssessments(departmentCode, components, errorProcessingDepartment = false)
          }
      }
  }

  private def generateAssessment(ac: AssessmentComponent)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]] = {
    assessmentService.getByTabulaAssessmentId(UUID.fromString(ac.id)).successFlatMapTo {
      case Some(existingAssessment) =>
        val updated = ac.asAssessment(Some(existingAssessment))
        if (updated == existingAssessment) Future.successful(ServiceResults.success(existingAssessment))
        else assessmentService.update(updated, Nil)

      case _ =>
        val newAssessment = ac.asAssessment(None)
        assessmentService.insert(newAssessment, Nil)
    }
  }
}


