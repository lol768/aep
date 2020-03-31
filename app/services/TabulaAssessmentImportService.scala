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
      logger.info(s"Processed Total departments: ${departmentWithAssessments.filterNot(_.errorProcessingDepartment).size}")
      AssessmentImportResult(departmentWithAssessments)
    }.recover {
      case e =>
        logger.error(s"Error processing departments API ${e.getMessage}")
        ServiceResults.exceptionToServiceResult(e)
    }
  }

  private def generateAssessment(ac: AssessmentComponent): Future[ServiceResult[Assessment]] = {
    assessmentService.getByTabulaAssessmentId(UUID.fromString(ac.id)).successFlatMapTo { optionalAssessment =>
      optionalAssessment match {
        case Some(existingAssessment) =>
          //TODO  - Probably should check tabula ac has modified from last time based on some fields and then only update otherwise can just return existingAssessment
          assessmentService.update(ac.asAssessment(Some(existingAssessment)), Nil).successFlatMapTo { row => Future.successful(Right(row)) }
        case _ => {
          val newAssessment = ac.asAssessment(None)
          assessmentService.insert(newAssessment, newAssessment.brief).successFlatMapTo { row =>
            Future.successful(Right(row))
          }
        }
      }
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
            logger.error(s"Error processing assessments of department $departmentCode, ${e.getMessage}")
            // If there are db errors still need to proceed for other departments, just log department has failure
            ServiceResults.success(DepartmentWithAssessments(departmentCode, Nil, true))
        }
      }
    ))
  }

}


