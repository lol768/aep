package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{AssessmentMetadata, Sitting, StudentAssessment}
import domain.dao.{AssessmentDao, DaoRunner, StudentAssessmentDao}
import javax.inject.{Inject, Singleton}
import services.ReportingService.AssessmentReport
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

object ReportingService {
  case class AssessmentReport(
    expectedSittings: Seq[StudentAssessment],
    startedSittings: Seq[StudentAssessment],
    notStartedSittings: Seq[StudentAssessment],
    submittedSittings: Seq[StudentAssessment],
    finalisedSittings: Seq[StudentAssessment]
  )
}

@ImplementedBy(classOf[ReportingServiceImpl])
trait ReportingService {
  def last48HrsAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]
  def startedAndSubmittableAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]
  def assessmentReport(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[AssessmentReport]]
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
  timingInfo: TimingInfoService,
)(implicit ec: ExecutionContext) extends ReportingService {

  override def last48HrsAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] =
    daoRunner.run(assDao.getLast48Hrs).map(_.map(_.asAssessmentMetadata)).map(ServiceResults.success)

  override def startedAndSubmittableAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] =
    assessmentService.getStartedAndSubmittable.map(_.map(_.map(_.asAssessmentMetadata)))

  private def expectedSittingsFull(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]] =
    sittingService.sittingsByAssessmentId(assessment)

  override def assessmentReport(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[AssessmentReport]] = {
    expectedSittingsFull(assessment).successMapTo(sittings =>
      AssessmentReport(
        expectedSittings = sittings.map(_.studentAssessment),
        startedSittings = sittings.filter(_.studentAssessment.startTime.isDefined).map(_.studentAssessment),
        notStartedSittings = sittings.filter(_.studentAssessment.startTime.isEmpty).map(_.studentAssessment),
        submittedSittings = sittings.filter(_.studentAssessment.uploadedFiles.nonEmpty).map(_.studentAssessment),
        finalisedSittings = sittings.filter(_.finalised(timingInfo.lateSubmissionPeriod)).map(_.studentAssessment)
      )
    )
  }

  override def expectedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    assessmentReport(assessment).successMapTo(_.expectedSittings)

  override def startedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    assessmentReport(assessment).successMapTo(_.startedSittings)

  override def notStartedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    assessmentReport(assessment).successMapTo(_.notStartedSittings)

  override def submittedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    assessmentReport(assessment).successMapTo(_.submittedSittings)

  override def finalisedSittings(assessment: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    assessmentReport(assessment).successMapTo(_.finalisedSittings)

}
