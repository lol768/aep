package services.job

import java.util.UUID
import javax.inject.Inject
import org.quartz.{JobExecutionContext, Scheduler}
import services.AssessmentService
import services.tabula.TabulaAssessmentService
import warwick.core.helpers.ServiceResults
import warwick.core.system.AuditLogContext
import warwick.core.helpers.ServiceResults.Implicits._

import scala.concurrent.{ExecutionContext, Future}


class UploadSubmissionsJob @Inject()(
  assessmentService: AssessmentService,
  tabulaAssessmentService: TabulaAssessmentService,
  scheduler: Scheduler,
)(implicit ec: ExecutionContext) extends AbstractJob(scheduler) {

  override def getDescription(context: JobExecutionContext): String = "Upload submissions"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {

    val assessmentId = UUID.fromString(context.getMergedJobDataMap.getString("id"))

    logger.info(s"[$assessmentId] sending submissions to Tabula")

    assessmentService.get(assessmentId).successFlatMapTo { assessment =>
      val assessmentWithAssignment = if (assessment.tabulaAssignments.isEmpty)
        tabulaAssessmentService.generateAssignments(assessment.asAssessmentMetadata)
      else
        Future.successful(ServiceResults.success(assessment))
      assessmentWithAssignment.successFlatMapTo { a => tabulaAssessmentService.generateAssignmentSubmissions(a, None) }
    }.map { result =>
      result.left.foreach { errors =>
        val message = errors.map(_.message).mkString("; ")
        logger.error(s"Errors sending submissions to tabula for $assessmentId: $message")
        errors.find(_.cause.nonEmpty).flatMap(_.cause).map(throwable =>
          throw new UnsupportedOperationException(throwable)
        ).getOrElse(
          throw new UnsupportedOperationException(message)
        )
      }
      JobResult.quiet
    }
  }

}
