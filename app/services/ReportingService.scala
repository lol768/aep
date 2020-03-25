package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.dao.{AssessmentDao, DaoRunner, StudentAssessmentDao}
import domain.{AssessmentMetadata, StudentAssessmentMetadata}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ReportingServiceImpl])
trait ReportingService {
  def todayAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]
  def liveAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]
  def expectedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentMetadata]]]
  def startedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentMetadata]]]
  def submittedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentMetadata]]]
}

@Singleton
class ReportingServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  assDao: AssessmentDao,
  sittingDao: StudentAssessmentDao,
  uploadedFileService: UploadedFileService,
  assessmentService: AssessmentService
)(implicit ec: ExecutionContext) extends ReportingService {

  override def todayAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] =
    daoRunner.run(assDao.getToday).map(_.map(_.asAssessmentMetadata)).map(ServiceResults.success)

  override def liveAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] =
    daoRunner.run(assDao.getInWindow).map(_.map(_.asAssessmentMetadata)).map(ServiceResults.success)

  override def expectedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentMetadata]]] =
    daoRunner.run(sittingDao.getMetadataByAssessmentId(assessment)).map(ServiceResults.success)

  override def startedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentMetadata]]] =
    expectedSittings(assessment).successMapTo(sittings => sittings.filter(_.inSeat == true))

  override def submittedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentMetadata]]] =
    expectedSittings(assessment).successMapTo(sittings => sittings.filter(_.uploadedFileCount > 0))
}