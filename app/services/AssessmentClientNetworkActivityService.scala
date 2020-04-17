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

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentClientNetworkActivityServiceImpl])
trait AssessmentClientNetworkActivityService {
  def record(assessmentClientNetworkActivity: AssessmentClientNetworkActivity)(implicit t: AuditLogContext): Future[ServiceResult[Done]]

  def findByStudentAssessmentId(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]]

  def getClientActivityFor(assessments: Seq[StudentAssessment], startDate: Option[OffsetDateTime], endDate: Option[OffsetDateTime], page: Page)(implicit t: TimingContext): Future[ServiceResult[(Int, Seq[AssessmentClientNetworkActivity])]]

  def getLatestActivityFor(studentAssessmentIds: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, AssessmentClientNetworkActivity]]]

  def getLatestInvigilatorActivityFor(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]]
}

@Singleton
class AssessmentClientNetworkActivityServiceImpl @Inject()(
  auditService: AuditService,
  dao: AssessmentClientNetworkActivityDao,
  daoRunner: DaoRunner
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityService {

  override def record(assessmentClientNetworkActivity: AssessmentClientNetworkActivity)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.StudentAssessment.RecordNetworkActivity, assessmentClientNetworkActivity.studentAssessmentId.toString, Target.StudentAssessment, Json.toJson(assessmentClientNetworkActivity)(AssessmentClientNetworkActivity.writesAssessmentClientNetworkActivity)) {
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

  override def getLatestActivityFor(studentAssessmentIds: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Map[UUID, AssessmentClientNetworkActivity]]] = {
    daoRunner.run(dao.getLatestActivityFor(studentAssessmentIds)).map { activities =>
      ServiceResults.success(activities.map(a => a.studentAssessmentId.get -> a).toMap)
    }
  }

  override def getLatestInvigilatorActivityFor(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentClientNetworkActivity]]] = {
    daoRunner.run(dao.getLatestInvigilatorActivityFor(assessmentId)).map { activities =>
      ServiceResults.success(activities)
    }
  }
}
