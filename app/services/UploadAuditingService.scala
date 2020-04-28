package services

import java.util.UUID

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

@ImplementedBy(classOf[UploadAuditingServiceImpl])
trait UploadAuditingService {
  def logAttempt(attempt: UploadAttempt)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]]
  def logCancellation(id: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class UploadAuditingServiceImpl @Inject()(
  auditService: AuditService,
) extends UploadAuditingService {

  import UploadAttempt._

  override def logAttempt(attempt: UploadAttempt)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]] = {
    auditService.audit(Operation.StudentAssessment.AttemptUpload, attempt.studentAssessmentId.toString, Target.StudentAssessment, Json.toJsObject(attempt)){
      Future.successful(ServiceResults.success(Done))
    }
  }

  override def logCancellation(id: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]] = {
    auditService.audit(Operation.StudentAssessment.CancelUpload, id.toString, Target.StudentAssessment, Json.obj()){
      Future.successful(ServiceResults.success(Done))
    }
  }
}
