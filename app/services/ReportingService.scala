package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{AssessmentMetadata, Sitting, StudentAssessment}
import domain.dao.{AssessmentDao, DaoRunner, StudentAssessmentDao}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ReportingServiceImpl])
trait ReportingService {
  def todayAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]
  def startedAndSubmittableAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]
  def expectedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def startedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def notStartedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def submittedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def finalisedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
}

@Singleton
class ReportingServiceImpl @Inject()(
  daoRunner: DaoRunner,
  assDao: AssessmentDao,
  assessmentService: AssessmentService,
  sittingService: StudentAssessmentService,
  sittingDao: StudentAssessmentDao,
)(implicit ec: ExecutionContext) extends ReportingService {

  override def todayAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] =
    daoRunner.run(assDao.getToday).map(_.map(_.asAssessmentMetadata)).map(ServiceResults.success)

  override def startedAndSubmittableAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] =
    assessmentService.getStartedAndSubmittable.map(_.map(_.map(_.asAssessmentMetadata)))

  private def expectedSittingsFull(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]] =
    sittingService.sittingsByAssessmentId(assessment)

  override def expectedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    expectedSittingsFull(assessment)
      .successMapTo(_.map(_.studentAssessment))

  override def startedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    expectedSittingsFull(assessment).successMapTo(sittings => sittings.filter(_.studentAssessment.startTime.isDefined))
      .successMapTo(_.map(_.studentAssessment))

  override def notStartedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    expectedSittingsFull(assessment).successMapTo(sittings => sittings.filter(_.studentAssessment.startTime.isEmpty))
      .successMapTo(_.map(_.studentAssessment))

  override def submittedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    expectedSittingsFull(assessment).successMapTo(sittings => sittings.filter(_.studentAssessment.uploadedFiles.nonEmpty))
      .successMapTo(_.map(_.studentAssessment))

  override def finalisedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    expectedSittingsFull(assessment).successMapTo(sittings => sittings.filter(_.finalised))
      .successMapTo(_.map(_.studentAssessment))
}
