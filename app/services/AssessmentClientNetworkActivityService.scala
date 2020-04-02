package services

import java.time.OffsetDateTime
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, ClientNetworkInformation}
import domain.dao.{AssessmentClientNetworkActivityDao, DaoRunner}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

@ImplementedBy(classOf[AssessmentClientNetworkActivityServiceImpl])
trait AssessmentClientNetworkActivityService {
  def record(clientNetworkInformation: ClientNetworkInformation, studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Done]]
 // def findByStudentAssessmentId(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]]
}

@Singleton
class AssessmentClientNetworkActivityServiceImpl @Inject()(
  dao: AssessmentClientNetworkActivityDao,
  daoRunner: DaoRunner
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityService {

  override def record(clientNetworkInformation: ClientNetworkInformation, studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Done]] = {
    val stored = AssessmentClientNetworkActivity(
      downlink = clientNetworkInformation.downlink,
      downlinkMax = clientNetworkInformation.downlinkMax,
      effectiveType = clientNetworkInformation.effectiveType,
      rtt = clientNetworkInformation.rtt,
      `type` = clientNetworkInformation.`type`,
      studentAssessmentId = studentAssessmentId,
      timestamp = JavaTime.offsetDateTime
    )

    daoRunner.run(dao.insert(stored)).map(_ => ServiceResults.success(Done))
  }

//  override def findByStudentAssessmentId(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]] = {
//
//  }
}
