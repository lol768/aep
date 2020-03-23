package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{StudentAssessment, StudentAssessmentWithAssessment}
import warwick.core.helpers.ServiceResults.Implicits._
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.dao.{DaoRunner, StudentAssessmentDao}
import javax.inject.{Inject, Singleton}
import system.routes.Types.UniversityID
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[StudentAssessmentServiceImpl])
trait StudentAssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentWithAssessment]]]
}

@Singleton
class StudentAssessmentServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: StudentAssessmentDao,
  uploadedFileService: UploadedFileService,
  assessmentService: AssessmentService
)(implicit ec: ExecutionContext) extends StudentAssessmentService {

  private def inflateWithUploadedFiles(storedStudentAssessments: Seq[StoredStudentAssessment])(implicit t: TimingContext) =
    uploadedFileService.get(storedStudentAssessments.flatMap(_.uploadedFiles)).map { uploadedFiles =>
      storedStudentAssessments.map(_.asStudentAssessment(uploadedFiles.map(f => f.id -> f).toMap))
    }

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    daoRunner.run(dao.all).flatMap(inflateWithUploadedFiles).map(ServiceResults.success)

  override def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    daoRunner.run(dao.getByAssessmentId(assessmentId)).flatMap(inflateWithUploadedFiles).map(ServiceResults.success)

  override def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentWithAssessment]]] = {
    daoRunner.run(dao.getByUniversityId(universityId)).flatMap(inflateWithUploadedFiles).flatMap { studentAssessments =>
      assessmentService.getByIds(studentAssessments.map(_.assessment)).successMapTo { assessments =>
        val assessmentsMap = assessments.map(a => a.id -> a).toMap
        studentAssessments.map(sa => StudentAssessmentWithAssessment(sa, assessmentsMap(sa.assessment)))
      }
    }
  }
}
