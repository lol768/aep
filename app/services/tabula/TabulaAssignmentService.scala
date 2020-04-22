package services.tabula

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.Assessment
import domain.dao.TabulaAssignmentTables.StoredTabulaAssignment
import domain.dao.{DaoRunner, TabulaAssignmentDao}
import domain.tabula.TabulaAssignment
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TabulaAssignmentServiceImpl])
trait TabulaAssignmentService {
  def getByAssessment(assessment: Assessment)(implicit t: TimingContext): Future[ServiceResult[Seq[TabulaAssignment]]]
  def save(tabulaAssignment: TabulaAssignment)(implicit ac: AuditLogContext): Future[ServiceResult[TabulaAssignment]]
}

@Singleton
class TabulaAssignmentServiceImpl @Inject()(
  daoRunner: DaoRunner,
  dao: TabulaAssignmentDao,
)(implicit ec: ExecutionContext) extends TabulaAssignmentService {

  override def getByAssessment(assessment: Assessment)(implicit t: TimingContext): Future[ServiceResult[Seq[TabulaAssignment]]] = {
    daoRunner.run(dao.getByIds(assessment.tabulaAssignments)).map(_.map(_.asTabulaAssignment)).map(ServiceResults.success)
  }

  override def save(tabulaAssignment: TabulaAssignment)(implicit ac: AuditLogContext): Future[ServiceResult[TabulaAssignment]] = {
    val stored = StoredTabulaAssignment(
      id = tabulaAssignment.id,
      name = tabulaAssignment.name,
      academicYear = tabulaAssignment.academicYear,
      created = JavaTime.offsetDateTime,
      version = JavaTime.offsetDateTime
    )

    daoRunner.run(dao.insert(stored)).map(_ => ServiceResults.success(tabulaAssignment))
  }
}
