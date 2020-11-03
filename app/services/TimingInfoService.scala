package services

import java.time.Duration
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.UploadAttempt
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import system.Features
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}

import scala.concurrent.Future

@ImplementedBy(classOf[TimingInfoServiceImpl])
trait TimingInfoService {
  def lateSubmissionPeriod: Duration
}

@Singleton
class TimingInfoServiceImpl @Inject()(
  features: Features
) extends TimingInfoService {

  // Students are allowed 2 extra hours after the official finish time of the exam
  // for them to make submissions. Anything submitted during this period should be
  // marked as LATE though.
  // If we have the feature flag active to import extra time adjustments from SITS,
  // the late period should be removed entirely.
  override def lateSubmissionPeriod: Duration =
    if (features.importStudentExtraTime) Duration.ofHours(0L)
    else Duration.ofHours(2L)

}
