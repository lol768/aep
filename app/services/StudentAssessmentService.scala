package services

import java.util.UUID

import slick.dbio.DBIO
import com.google.inject.ImplementedBy
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.dao.{AssessmentDao, DaoRunner, StudentAssessmentDao}
import domain.{Assessment, StudentAssessment, StudentAssessmentWithAssessment}
import domain.AuditEvent._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import system.routes.Types.UniversityID
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[StudentAssessmentServiceImpl])
trait StudentAssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentWithAssessment]]]
  def getWithAssessment(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[StudentAssessmentWithAssessment]
  def startAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
}

@Singleton
class StudentAssessmentServiceImpl @Inject()(
  audit: AuditService,
  daoRunner: DaoRunner,
  dao: StudentAssessmentDao,
  uploadedFileService: UploadedFileService,
  assessmentService: AssessmentService,
  assessmentDao: AssessmentDao,
)(implicit ec: ExecutionContext) extends StudentAssessmentService {

  private def inflateWithUploadedFiles(storedStudentAssessments: Seq[StoredStudentAssessment])(implicit t: TimingContext) =
    uploadedFileService.get(storedStudentAssessments.flatMap(_.uploadedFiles)).map { uploadedFiles =>
      storedStudentAssessments.map(_.asStudentAssessment(uploadedFiles.map(f => f.id -> f).toMap))
    }

  private def inflateWithUploadedFiles(storedStudentAssessment: StoredStudentAssessment)(implicit t: TimingContext) =
    uploadedFileService.get(storedStudentAssessment.uploadedFiles).map { uploadedFiles =>
      storedStudentAssessment.asStudentAssessment(uploadedFiles.map(f => f.id -> f).toMap)
    }

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    daoRunner.run(dao.all).flatMap(inflateWithUploadedFiles).map(ServiceResults.success)

  override def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    daoRunner.run(dao.getByAssessmentId(assessmentId)).flatMap(inflateWithUploadedFiles).map(ServiceResults.success)

  override def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentWithAssessment]]] = {
    daoRunner.run(dao.getByUniversityId(universityId)).flatMap(inflateWithUploadedFiles).flatMap { studentAssessments =>
      assessmentService.getByIds(studentAssessments.map(_.assessmentId)).successMapTo { assessments =>
        val assessmentsMap = assessments.map(a => a.id -> a).toMap
        studentAssessments.map(sa => StudentAssessmentWithAssessment(sa, assessmentsMap(sa.assessmentId)))
      }
    }
  }

  override def getWithAssessment(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[StudentAssessmentWithAssessment] = {
    daoRunner.run(
      for {
        studentAssessment <- dao.get(universityId, assessmentId)
        assessment <- assessmentDao.getById(assessmentId)
      } yield (studentAssessment, assessment)
    ).flatMap {
      case (storedStudentAssessment: StoredStudentAssessment, storedAssessment: StoredAssessment) => {
        val studentAssessmentFuture = inflateWithUploadedFiles(storedStudentAssessment)

        val assessmentFuture = uploadedFileService.get(storedAssessment.storedBrief.fileIds).map { uploadedFiles =>
          storedAssessment.asAssessment(uploadedFiles.map(f => f.id -> f).toMap)
        }
        for {
          studentAssessment <- studentAssessmentFuture
          assessment <- assessmentFuture
        } yield StudentAssessmentWithAssessment(studentAssessment, assessment)
      }
    }
  }

  private def canStart(storedAssessment: StoredAssessment, storedStudentAssessment: StoredStudentAssessment): Future[Unit] = Future.successful {
    require(storedAssessment.startTime.exists(_.isBefore(JavaTime.offsetDateTime)), "Cannot start assessment, too early")
    require(storedAssessment.startTime.exists(_.plus(Assessment.window).isAfter(JavaTime.offsetDateTime)), "Cannot start assessment, too late")
  }

  override def startAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.Assessment.StartAssessment, studentAssessment.assessmentId.toString, Target.StudentAssessment, Json.obj(("universityId", studentAssessment.studentId.string))){
      daoRunner.run(
        for {
          storedStudentAssessment <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessment <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(canStart(storedAssessment, storedStudentAssessment))
          updatedStudentAssessment <- {
            if(storedStudentAssessment.startTime.isEmpty) {
              dao.update(storedStudentAssessment.copy(startTime = Some(JavaTime.offsetDateTime)))
            } else {
              DBIO.successful(storedStudentAssessment)
            }
          }
        } yield updatedStudentAssessment
      ).flatMap(inflateWithUploadedFiles(_)).map(ServiceResults.success)
    }
  }
}
