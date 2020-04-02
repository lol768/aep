package services

import com.google.inject.ImplementedBy
import domain.Assessment
import domain.tabula.AssessmentComponent
import javax.inject.{Inject, Singleton}
import play.api.Configuration
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
  assessmentService: AssessmentService,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends TabulaAssessmentImportService with Logging {
  private[this] lazy val examProfileCodes = configuration.get[Seq[String]]("tabula.examProfileCodes")

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

    ServiceResults.futureSequence(
      examProfileCodes.map { examProfileCode =>
        tabulaAssessmentService.getAssessments(GetAssessmentsOptions(departmentCode, withExamPapersOnly = true, Some(examProfileCode)))
          .successFlatMapTo { assessmentComponents =>
            ServiceResults.futureSequence(assessmentComponents.map(generateAssessment(_, examProfileCode)))
          }
      }
    ).successMapTo(components => DepartmentWithAssessments(departmentCode, components.flatten.flatten, errorProcessingDepartment = false))
  }

  private def generateAssessment(ac: AssessmentComponent, examProfileCode: String)(implicit ctx: AuditLogContext): Future[ServiceResult[Option[Assessment]]] = {
    assessmentService.getByTabulaAssessmentId(ac.id, examProfileCode).successFlatMapTo {
      case Some(existingAssessment) =>
        ac.asAssessment(Some(existingAssessment), examProfileCode) match {
          case Some(notUpdated) if notUpdated == existingAssessment => Future.successful(ServiceResults.success(Some(existingAssessment)))
          case Some(updated) => assessmentService.update(updated, Nil).successMapTo(Some.apply)
          case _ => Future.successful(ServiceResults.success(None))
        }

      case _ =>
        ac.asAssessment(None, examProfileCode) match {
          case Some(newAssessment) => assessmentService.insert(newAssessment, Nil).successMapTo(Some.apply)
          case _ => Future.successful(ServiceResults.success(None))
        }
    }
  }
}


