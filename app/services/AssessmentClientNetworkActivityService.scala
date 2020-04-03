package services

import java.time.OffsetDateTime
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, Page, StudentAssessment}
import domain.dao.{AssessmentClientNetworkActivityDao, DaoRunner}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

@ImplementedBy(classOf[AssessmentClientNetworkActivityServiceImpl])
trait AssessmentClientNetworkActivityService {
  def record(assessmentClientNetworkActivity: AssessmentClientNetworkActivity)(implicit t: TimingContext): Future[ServiceResult[Done]]
  def findByStudentAssessmentId(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]]
  def getClientActivityFor(assessments: Seq[StudentAssessment], startDate:Option[OffsetDateTime], endDate: Option[OffsetDateTime], page: Page)(implicit t: TimingContext): Future[ServiceResult[(Int,Seq[AssessmentClientNetworkActivity])]]
}

@Singleton
class AssessmentClientNetworkActivityServiceImpl @Inject()(
  dao: AssessmentClientNetworkActivityDao,
  daoRunner: DaoRunner
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityService {

  override def record(assessmentClientNetworkActivity: AssessmentClientNetworkActivity)(implicit t: TimingContext): Future[ServiceResult[Done]] = {
    daoRunner.run(dao.insert(assessmentClientNetworkActivity)).map(_ => ServiceResults.success(Done))
  }

  override def findByStudentAssessmentId(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]] = {
    daoRunner.run(dao.findByStudentAssessmentId(studentAssessmentId)).map(Right.apply)
  }

  override  def getClientActivityFor(assessments: Seq[StudentAssessment], startDate:Option[OffsetDateTime], endDate: Option[OffsetDateTime], page: Page)(implicit t: TimingContext): Future[ServiceResult[(Int,Seq[AssessmentClientNetworkActivity])]] = {

    def activitiesWithFiler = for {
      activities <- dao.getClientActivityFor(assessments, startDate, endDate, page.offset, page.maxResults)
      total <- dao.countClientActivityFor(assessments, startDate, endDate)
    } yield (total,activities)

    daoRunner.run(activitiesWithFiler.map(ServiceResults.success))
  }
}
