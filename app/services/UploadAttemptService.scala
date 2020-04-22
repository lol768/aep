package services

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.dao.{AssessmentClientNetworkActivityDao, DaoRunner}
import domain.{AssessmentClientNetworkActivity, Page, StudentAssessment}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.TimingContext
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UploadAttemptServiceImpl])
trait UploadAttemptService {
  def logAttempt()
}

@Singleton
class UploadAttemptServiceImpl @Inject()(
  auditService: AuditService,
) extends UploadAttemptService {
  override def logAttempt(): Unit = {

  }
}
