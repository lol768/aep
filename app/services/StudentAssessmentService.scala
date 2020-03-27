package services

import java.util.UUID

import com.google.common.io.ByteSource
import slick.dbio.DBIO
import com.google.inject.ImplementedBy
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.dao.{AssessmentDao, DaoRunner, StudentAssessmentDao}
import domain.{Assessment, StudentAssessment, StudentAssessmentWithAssessment, StudentAssessmentWithAssessmentMetadata, UploadedFileOwner}
import domain.AuditEvent._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import system.routes.Types.UniversityID
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[StudentAssessmentServiceImpl])
trait StudentAssessmentService {
  def get(studentId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Option[StudentAssessment]]]
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessmentWithAssessment]]]
  def getWithAssessment(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[Option[StudentAssessmentWithAssessment]]
  def getMetadataWithAssessment(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[StudentAssessmentWithAssessmentMetadata]
  def getMetadataWithAssessment(universityId: UniversityID)(implicit t: TimingContext): Future[Seq[StudentAssessmentWithAssessmentMetadata]]
  def startAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def finishAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def attachFilesToAssessment(studentAssessment: StudentAssessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def deleteAttachedFile(studentAssessment: StudentAssessment, file: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def upsert(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
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

  override def get(studentId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Option[StudentAssessment]]] = {
    daoRunner.run(dao.get(studentId, assessmentId))
      .flatMap(_.map(inflateWithUploadedFiles) match {
        case Some(f) => f.map(Some(_))
        case None => Future.successful(None)
      }).map(ServiceResults.success)
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

  override def getWithAssessment(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[Option[StudentAssessmentWithAssessment]] = {
    daoRunner.run(
      for {
        studentAssessment <- dao.get(universityId, assessmentId)
        assessment <- assessmentDao.getById(assessmentId)
      } yield (studentAssessment, assessment)
    ).flatMap {
      case (storedStudentAssessment: Some[StoredStudentAssessment], storedAssessment: Some[StoredAssessment]) => {
        val studentAssessmentFuture = inflateWithUploadedFiles(storedStudentAssessment.get)

        val assessmentFuture = uploadedFileService.get(storedAssessment.get.storedBrief.fileIds).map { uploadedFiles =>
          storedAssessment.get.asAssessment(uploadedFiles.map(f => f.id -> f).toMap)
        }
        for {
          studentAssessment <- studentAssessmentFuture
          assessment <- assessmentFuture
        } yield Some(StudentAssessmentWithAssessment(studentAssessment, assessment))
      }
      case _ => Future.successful(None)
    }
  }

  override def getMetadataWithAssessment(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[StudentAssessmentWithAssessmentMetadata] =
    daoRunner.run(
      for {
        studentAssessment <- dao.get(universityId, assessmentId)
        assessment <- assessmentDao.getById(assessmentId)
      } yield StudentAssessmentWithAssessmentMetadata(
        studentAssessment.getOrElse(noStudentAssessmentFound(assessmentId, universityId))
          .asStudentAssessmentMetadata,
        assessment.getOrElse(noAssessmentFound(assessmentId))
          .asAssessmentMetadata)
    )

  override def getMetadataWithAssessment(universityId: UniversityID)(implicit t: TimingContext): Future[Seq[StudentAssessmentWithAssessmentMetadata]] = {
    daoRunner.run(
      for {
        studentAssessments <- dao.getByUniversityId(universityId)
        assessments <- assessmentDao.getByIds(studentAssessments.map(_.assessmentId))
      } yield {
        val assessmentsMap = assessments.map(a => a.id -> a.asAssessmentMetadata).toMap
        studentAssessments.map(sA => StudentAssessmentWithAssessmentMetadata(sA.asStudentAssessmentMetadata, assessmentsMap(sA.assessmentId)))
      }
    )
  }

  private def canStart(storedAssessment: StoredAssessment, storedStudentAssessment: StoredStudentAssessment): Future[Unit] = Future.successful {
    require(storedAssessment.startTime.exists(_.isBefore(JavaTime.offsetDateTime)), "Cannot start assessment, too early")
  }

  private def startedNotFinalised(storedAssessment: StoredAssessment, storedStudentAssessment: StoredStudentAssessment): Future[Unit] = Future.successful {
    require(storedStudentAssessment.finaliseTime.isEmpty, "Cannot perform action, assessment is finalised")
    require(storedStudentAssessment.startTime.isDefined, "Cannot perform action, assessment is not yet started")
  }

  override def startAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.Assessment.StartAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string)){
      daoRunner.run(
        for {
          storedStudentAssessmentOption <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessmentOption <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(canStart(
            storedAssessmentOption.getOrElse(noAssessmentFound(studentAssessment.assessmentId)),
            storedStudentAssessmentOption.getOrElse(noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId))
          ))
          updatedStudentAssessment <- {
            storedStudentAssessmentOption.map { storedStudentAssessment =>
              if(storedStudentAssessment.startTime.isEmpty) {
                dao.update(storedStudentAssessment.copy(startTime = Some(JavaTime.offsetDateTime)))
              } else {
                DBIO.successful(storedStudentAssessment)
              }
            }.getOrElse{
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          }
        } yield updatedStudentAssessment
      ).flatMap(inflateWithUploadedFiles(_)).map(ServiceResults.success)
    }
  }

  override def finishAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.Assessment.FinishAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string)){
      daoRunner.run(
        for {
          storedStudentAssessmentOption <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessmentOption <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(canStart(
            storedAssessmentOption.getOrElse {
              noAssessmentFound(studentAssessment.assessmentId)
            },
            storedStudentAssessmentOption.getOrElse {
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          ))
          updatedStudentAssessment <- {
            storedStudentAssessmentOption.map { storedStudentAssessment =>
              if(storedStudentAssessment.startTime.isDefined) {
                dao.update(storedStudentAssessment.copy(finaliseTime = Some(JavaTime.offsetDateTime)))
              } else {
                DBIO.failed(new IllegalArgumentException("Cannot finalise an assessment which has not been started"))
              }
            }.getOrElse {
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          }
        } yield updatedStudentAssessment
      ).flatMap(inflateWithUploadedFiles(_)).map(ServiceResults.success)
    }
  }

  override def attachFilesToAssessment(studentAssessment: StudentAssessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] =
    audit.audit(Operation.Assessment.AttachFilesToAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string, "files" -> files.map(_._2.fileName))) {
      daoRunner.run(
        for {
          storedStudentAssessment <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessment <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(startedNotFinalised(
            storedAssessment.getOrElse(noAssessmentFound(studentAssessment.assessmentId)),
            storedStudentAssessment.getOrElse(noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId))
          ))
          fileIds <- DBIO.sequence(files.toList.map { case (in, metadata) =>
            uploadedFileService.storeDBIO(
              in,
              metadata,
              ctx.usercode.get,
              storedStudentAssessment.getOrElse(noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)).id,
              UploadedFileOwner.StudentAssessment
            ).map(_.id)
          })
          updatedStudentAssessment <- dao.update( storedStudentAssessment.map { ssa =>
            ssa.copy(uploadedFiles = ssa.uploadedFiles ::: fileIds)
          }.getOrElse {
            noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
          })
        } yield updatedStudentAssessment
      ).flatMap(inflateWithUploadedFiles(_)).map(ServiceResults.success)
    }

  override def deleteAttachedFile(studentAssessment: StudentAssessment, file: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.Assessment.DeleteAttachedAssessmentFile, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string, "fileId" -> file.toString)) {
      daoRunner.run(
        for {
          storedStudentAssessment <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessment <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(startedNotFinalised(
            storedAssessment.getOrElse(noAssessmentFound(studentAssessment.assessmentId)),
            storedStudentAssessment.getOrElse(noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId))
          ))
          // TODO: do we want to delete the object storage file?
          updatedStudentAssessment <- dao.update(
            storedStudentAssessment.map { ssa =>
              ssa.copy(uploadedFiles = ssa.uploadedFiles.filterNot(_ == file))
            }.getOrElse{
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          )
        } yield updatedStudentAssessment
      ).flatMap(inflateWithUploadedFiles(_)).map(ServiceResults.success)
    }
  }

  override def upsert(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    daoRunner.run(dao.get(studentAssessment.studentId, studentAssessment.assessmentId)).flatMap { result =>
      result.map { existingSA =>
        daoRunner.run(dao.update(existingSA.copy(
          inSeat = studentAssessment.inSeat,
          startTime = studentAssessment.startTime,
          finaliseTime = studentAssessment.finaliseTime,
          uploadedFiles = studentAssessment.uploadedFiles.map(_.id).toList,
        )))
      }.getOrElse {
        val timestamp = JavaTime.offsetDateTime
        daoRunner.run(dao.insert(StoredStudentAssessment(
          id = studentAssessment.id,
          assessmentId = studentAssessment.assessmentId,
          studentId = studentAssessment.studentId,
          inSeat = studentAssessment.inSeat,
          startTime = studentAssessment.startTime,
          finaliseTime = studentAssessment.finaliseTime,
          uploadedFiles = studentAssessment.uploadedFiles.map(_.id).toList,
          created = timestamp,
          version = timestamp,
        )))
      }.flatMap(inflateWithUploadedFiles)
        .map(ServiceResults.success)
    }
  }

  private def noAssessmentFound(id: UUID) =
    throw new NoSuchElementException(s"Could not find an assessment with id ${id.toString}")

  private def noStudentAssessmentFound(assessmentId: UUID, studentId: UniversityID) =
    throw new NoSuchElementException(s"Could not find student assessment with id ${assessmentId.toString} and student id ${studentId.string}")
}
