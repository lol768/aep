package services

import akka.Done
import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.UploadAttempt
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}

import scala.concurrent.Future

@ImplementedBy(classOf[UploadAttemptServiceImpl])
trait UploadAttemptService {
  def logAttempt(attempt: UploadAttempt)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class UploadAttemptServiceImpl @Inject()(
  auditService: AuditService,
) extends UploadAttemptService {
  import UploadAttempt._
  override def logAttempt(attempt: UploadAttempt)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]] = {
    auditService.audit(Operation.StudentAssessment.AttemptUpload, attempt.studentAssessmentId.toString, Target.StudentAssessment, Json.toJsObject(attempt)){
      Future.successful(ServiceResults.success(Done))
    }
  }
}
