package services

import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain._
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.{StoredDeclarations, StoredStudentAssessment}
import domain.dao._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import services.StudentAssessmentService._
import slick.dbio.DBIO
import system.Features
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[StudentAssessmentServiceImpl])
trait StudentAssessmentService {
  def get(studentId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[StudentAssessment]]
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def sittingsByAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]]
  def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]]
  def byUniversityIds(universityIds: Seq[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]]
  def sittingsByUniversityIds(universityIds: Seq[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]]
  def getSitting(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Option[Sitting]]]
  def getSittingsMetadata(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[SittingMetadata]]
  def getSittingsMetadata(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[SittingMetadata]]]
  def startAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def finishAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def attachFilesToAssessment(studentAssessment: StudentAssessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def deleteAttachedFile(studentAssessment: StudentAssessment, file: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def insert(studentAssessments: Set[StudentAssessment])(implicit ctx: AuditLogContext): Future[ServiceResult[Set[StudentAssessment]]]
  def upsert(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]]
  def delete(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]]

  def getOrDefaultDeclarations(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Declarations]]
  def getDeclarations(studentAssessmentIds: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Declarations]]]
  def upsert(declarations: Declarations)(implicit ctx: AuditLogContext): Future[ServiceResult[Declarations]]
}

@Singleton
class StudentAssessmentServiceImpl @Inject()(
  audit: AuditService,
  daoRunner: DaoRunner,
  dao: StudentAssessmentDao,
  uploadedFileService: UploadedFileService,
  assessmentService: AssessmentService,
  assessmentDao: AssessmentDao,
  assessmentClientNetworkActivityDao: AssessmentClientNetworkActivityDao,
  timingInfo: TimingInfoService,
  features: Features,
)(implicit ec: ExecutionContext) extends StudentAssessmentService {


  override def get(studentId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[StudentAssessment]] =
    daoRunner.run(dao.loadWithUploadedFiles(studentId, assessmentId))
      .map(inflateRowWithUploadedFiles)
      .map {
        case Some(f) => ServiceResults.success(f)
        case None => noStudentAssessmentFound(assessmentId, studentId)
      }

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    daoRunner.run(dao.loadAllWithUploadedFiles)
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def byAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    daoRunner.run(dao.loadByAssessmentIdWithUploadedFiles(assessmentId))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def sittingsByAssessmentId(assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]] =
    daoRunner.run(dao.loadByAssessmentIdWithUploadedFiles(assessmentId))
      .map(inflateRowsWithUploadedFiles)
      .flatMap(convertToSittings)

  override def byUniversityId(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]] = {
    daoRunner.run(dao.loadByUniversityIdWithUploadedFiles(universityId))
      .map(inflateRowsWithUploadedFiles)
      .flatMap(convertToSittings)
  }

  override def byUniversityIds(universityIds: Seq[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Seq[StudentAssessment]]] = {
    daoRunner.run(dao.loadByUniversityIDsWithUploadedFiles(universityIds))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)
  }

  override def sittingsByUniversityIds(universityIds: Seq[UniversityID])(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]] =
    daoRunner.run(dao.loadByUniversityIDsWithUploadedFiles(universityIds))
      .map(inflateRowsWithUploadedFiles)
      .flatMap(convertToSittings)

  private def convertToSittings(studentAssessments: Seq[StudentAssessment])(implicit t: TimingContext): Future[ServiceResult[Seq[Sitting]]] = {
    val assessmentIds = studentAssessments.map(_.assessmentId).distinct
    val studentAssessmentIds = studentAssessments.map(_.id)

    ServiceResults.zip(
      assessmentService.getByIds(assessmentIds).successMapTo(_.map(a => a.id -> a).toMap),
      getDeclarations(studentAssessmentIds).successMapTo(_.map(d => d.studentAssessmentId -> d).toMap)
    ).successMapTo { case (assessmentsMap, declarationsMap) =>
      studentAssessments.map { sa =>
        Sitting(sa, assessmentsMap(sa.assessmentId), declarationsMap.getOrElse(sa.id, Declarations(sa.id)))
      }
    }
  }

  override def getSitting(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Option[Sitting]]] = {
    daoRunner.run(
      for {
        studentAssessmentRows <- dao.loadWithUploadedFiles(universityId, assessmentId)
        assessmentRows <- assessmentDao.loadByIdWithUploadedFiles(assessmentId)
      } yield (inflateRowWithUploadedFiles(studentAssessmentRows), AssessmentService.inflateRowWithUploadedFiles(assessmentRows))
    ).flatMap {
      case (Some(studentAssessment), Some(assessment)) =>
        getOrDefaultDeclarations(studentAssessment.id).successMapTo(declarations =>
          Some(Sitting(studentAssessment, assessment, declarations))
        )
      case _ =>
        Future.successful(ServiceResults.success(None))
    }
  }

  override def getSittingsMetadata(universityId: UniversityID, assessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[SittingMetadata]] =
    daoRunner.run(
      for {
        studentAssessmentRows <- dao.loadWithUploadedFiles(universityId, assessmentId)
        studentAssessment = inflateRowWithUploadedFiles(studentAssessmentRows)
        assessment <- assessmentDao.getById(assessmentId)
      } yield SittingMetadata(
        studentAssessment.getOrElse(noStudentAssessmentFound(assessmentId, universityId)),
        assessment.getOrElse(noAssessmentFound(assessmentId))
          .asAssessmentMetadata
      )
    ).map(ServiceResults.success)

  override def getSittingsMetadata(universityId: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Seq[SittingMetadata]]] = {
    daoRunner.run(
      for {
        studentAssessmentRows <- dao.loadByUniversityIdWithUploadedFiles(universityId)
        studentAssessments = StudentAssessmentService.inflateRowsWithUploadedFiles(studentAssessmentRows)
        assessments <- assessmentDao.getByIds(studentAssessments.map(_.assessmentId))
      } yield {
        val assessmentsMap = assessments.map(a => a.id -> a.asAssessmentMetadata).toMap
        studentAssessments.map(sA => SittingMetadata(sA, assessmentsMap(sA.assessmentId)))
      }
    ).map(ServiceResults.success)
  }

  private def assertTimeInRange(storedAssessment: StoredAssessment, storedStudentAssessment: StoredStudentAssessment): Future[Unit] = Future.successful {
    require(storedAssessment.hasStartTimePassed(), "Cannot do assessment, too early")
    require(!storedAssessment.hasDefaultLastAllowedStartTimePassed(timingInfo.lateSubmissionPeriod), "Cannot do assessment, too late")
  }

  private def hasAcceptedDeclarations(storedDeclarations: Option[StoredDeclarations]): Future[Unit] = Future.successful {
    require(storedDeclarations.exists(_.asDeclarations.acceptable(features)), "Cannot start assessment, declarations not made")
  }

  private def startedNotFinalised(storedAssessment: StoredAssessment, storedStudentAssessment: StoredStudentAssessment): Future[Unit] = Future.successful {
    require(storedStudentAssessment.finaliseTime.isEmpty, "Cannot perform action, assessment is finalised")
    require(storedStudentAssessment.startTime.isDefined, "Cannot perform action, assessment is not yet started")
  }

  override def startAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.StudentAssessment.StartAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string)){
      daoRunner.run(
        for {
          storedStudentAssessmentOption <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessmentOption <- assessmentDao.getById(studentAssessment.assessmentId)
          storedDeclarationsOption <- dao.getDeclarations(studentAssessment.id)
          _ <- DBIO.from(assertTimeInRange(
            storedAssessmentOption.getOrElse(noAssessmentFound(studentAssessment.assessmentId)),
            storedStudentAssessmentOption.getOrElse(noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId))
          ))
          _ <- DBIO.from(hasAcceptedDeclarations(storedDeclarationsOption))
          _ <- {
            storedStudentAssessmentOption.map { storedStudentAssessment =>
              if (storedStudentAssessment.startTime.isEmpty) {
                dao.update(storedStudentAssessment.copy(startTime = Some(JavaTime.offsetDateTime)))
              } else {
                DBIO.successful(storedStudentAssessment)
              }
            }.getOrElse {
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          }
          updatedStudentAssessmentRows <- dao.loadWithUploadedFiles(studentAssessment.studentId, studentAssessment.assessmentId)
        } yield updatedStudentAssessmentRows
      ).map(inflateRowWithUploadedFiles(_).get).map(ServiceResults.success)
    }
  }

  override def finishAssessment(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.StudentAssessment.FinishAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string)){
      daoRunner.run(
        for {
          storedStudentAssessmentOption <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessmentOption <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(assertTimeInRange(
            storedAssessmentOption.getOrElse {
              noAssessmentFound(studentAssessment.assessmentId)
            },
            storedStudentAssessmentOption.getOrElse {
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          ))
          _ <- {
            storedStudentAssessmentOption.map { storedStudentAssessment =>
              if (storedStudentAssessment.startTime.isDefined) {
                dao.update(storedStudentAssessment.copy(finaliseTime = Some(JavaTime.offsetDateTime)))
              } else {
                DBIO.failed(new IllegalArgumentException("Cannot finalise an assessment which has not been started"))
              }
            }.getOrElse {
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          }
          updatedStudentAssessmentRows <- dao.loadWithUploadedFiles(studentAssessment.studentId, studentAssessment.assessmentId)
        } yield updatedStudentAssessmentRows
      ).map(inflateRowWithUploadedFiles(_).get).map(ServiceResults.success)
    }
  }

  override def attachFilesToAssessment(studentAssessment: StudentAssessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] =
    audit.audit(Operation.StudentAssessment.AttachFilesToAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string, "files" -> files.map(_._2.fileName))) {
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
          _ <- dao.update(storedStudentAssessment.map { ssa =>
            ssa.copy(uploadedFiles = ssa.uploadedFiles ::: fileIds)
          }.getOrElse {
            noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
          })
          updatedStudentAssessmentRows <- dao.loadWithUploadedFiles(studentAssessment.studentId, studentAssessment.assessmentId)
        } yield updatedStudentAssessmentRows
      ).map(inflateRowWithUploadedFiles(_).get).map(ServiceResults.success)
    }

  override def deleteAttachedFile(studentAssessment: StudentAssessment, file: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] = {
    audit.audit(Operation.StudentAssessment.DeleteAttachedAssessmentFile, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string, "fileId" -> file.toString)) {
      daoRunner.run(
        for {
          storedStudentAssessment <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
          storedAssessment <- assessmentDao.getById(studentAssessment.assessmentId)
          _ <- DBIO.from(startedNotFinalised(
            storedAssessment.getOrElse(noAssessmentFound(studentAssessment.assessmentId)),
            storedStudentAssessment.getOrElse(noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId))
          ))
          // We don't delete the file from object storage, just de-reference it
          // TODO: Do we need to remove the owner from the file?
          _ <- dao.update(
            storedStudentAssessment.map { ssa =>
              ssa.copy(uploadedFiles = ssa.uploadedFiles.filterNot(_ == file))
            }.getOrElse{
              noStudentAssessmentFound(studentAssessment.assessmentId, studentAssessment.studentId)
            }
          )
          updatedStudentAssessmentRows <- dao.loadWithUploadedFiles(studentAssessment.studentId, studentAssessment.assessmentId)
        } yield updatedStudentAssessmentRows
      ).map(inflateRowWithUploadedFiles(_).get).map(ServiceResults.success)
    }
  }

  override def insert(studentAssessments: Set[StudentAssessment])(implicit ctx: AuditLogContext): Future[ServiceResult[Set[StudentAssessment]]] =
    if (studentAssessments.isEmpty) Future.successful(ServiceResults.success(Set.empty))
    else audit.audit(Operation.Assessment.CreateStudentAssessments, studentAssessments.head.assessmentId.toString, Target.Assessment, Json.obj("universityId" -> studentAssessments.map(_.studentId.string))) {
      val timestamp = JavaTime.offsetDateTime
      daoRunner.run(dao.insertAll(studentAssessments.map(studentAssessment => StoredStudentAssessment(
        id = studentAssessment.id,
        assessmentId = studentAssessment.assessmentId,
        occurrence = studentAssessment.occurrence,
        academicYear = studentAssessment.academicYear,
        studentId = studentAssessment.studentId,
        inSeat = studentAssessment.inSeat,
        startTime = studentAssessment.startTime,
        extraTimeAdjustmentPerHour = studentAssessment.extraTimeAdjustmentPerHour,
        finaliseTime = studentAssessment.explicitFinaliseTime,
        uploadedFiles = studentAssessment.uploadedFiles.map(_.id).toList,
        tabulaSubmissionId = None,
        created = timestamp,
        version = timestamp,
      )))).map(_.map(_.asStudentAssessment(Map.empty)))
        .map(r => ServiceResults.success(r.toSet))
    }

  override def upsert(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[StudentAssessment]] =
    audit.audit(Operation.StudentAssessment.UpdateStudentAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string)) {
      daoRunner.run(dao.get(studentAssessment.studentId, studentAssessment.assessmentId)).flatMap { result =>
        result.map { existingSA =>
          daoRunner.run(for {
            updated <- dao.update(StoredStudentAssessment(
              id = existingSA.id,
              assessmentId = existingSA.assessmentId,
              studentId = existingSA.studentId,
              occurrence = studentAssessment.occurrence,
              academicYear = studentAssessment.academicYear,
              inSeat = studentAssessment.inSeat,
              startTime = studentAssessment.startTime,
              extraTimeAdjustmentPerHour = studentAssessment.extraTimeAdjustmentPerHour,
              finaliseTime = studentAssessment.explicitFinaliseTime,
              uploadedFiles = studentAssessment.uploadedFiles.map(_.id).toList,
              tabulaSubmissionId = studentAssessment.tabulaSubmissionId,
              created = existingSA.created,
              version = existingSA.version
            ))
            withUploadedFiles <- dao.loadWithUploadedFiles(updated.studentId, updated.assessmentId)
          } yield inflateRowWithUploadedFiles(withUploadedFiles).get)
        }.getOrElse {
          val timestamp = JavaTime.offsetDateTime
          daoRunner.run(dao.insert(StoredStudentAssessment(
            id = studentAssessment.id,
            assessmentId = studentAssessment.assessmentId,
            occurrence = studentAssessment.occurrence,
            academicYear = studentAssessment.academicYear,
            studentId = studentAssessment.studentId,
            inSeat = studentAssessment.inSeat,
            startTime = studentAssessment.startTime,
            extraTimeAdjustmentPerHour = studentAssessment.extraTimeAdjustmentPerHour,
            finaliseTime = studentAssessment.explicitFinaliseTime,
            uploadedFiles = studentAssessment.uploadedFiles.map(_.id).toList,
            tabulaSubmissionId = studentAssessment.tabulaSubmissionId,
            created = timestamp,
            version = timestamp,
          ))).map(_.asStudentAssessment(Map.empty))
        }.map(ServiceResults.success)
      }
    }

  override def delete(studentAssessment: StudentAssessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Done]] =
    audit.audit(Operation.StudentAssessment.DeleteStudentAssessment, studentAssessment.id.toString, Target.StudentAssessment, Json.obj("universityId" -> studentAssessment.studentId.string)) {
      daoRunner.run(for {
        existing <- dao.get(studentAssessment.studentId, studentAssessment.assessmentId)
        _ <- DBIO.from(Future.successful {
          // The forall is true if existing doesn't exist, so this supports a delete for a deleted object as fine
          require(existing.forall(_.startTime.isEmpty), "Cannot perform action, student assessment has been started")
        })
        _ <- assessmentClientNetworkActivityDao.deleteAll(studentAssessment.id)
        deleted <- dao.delete(studentAssessment.studentId, studentAssessment.assessmentId)
      } yield deleted)
        .map(_ => ServiceResults.success(Done))
    }

  override def getOrDefaultDeclarations(studentAssessmentId: UUID)(implicit t: TimingContext): Future[ServiceResult[Declarations]] =
    daoRunner.run(dao.getDeclarations(studentAssessmentId))
      .map(_.map(_.asDeclarations).getOrElse(Declarations(studentAssessmentId)))
      .map(ServiceResults.success)

  override def getDeclarations(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Declarations]]] =
    daoRunner.run(dao.getDeclarations(ids))
      .map(_.map(_.asDeclarations))
      .map(ServiceResults.success)

  override def upsert(decs: Declarations)(implicit ctx: AuditLogContext): Future[ServiceResult[Declarations]] = {
    audit.audit(Operation.StudentAssessment.MakeDeclarations, decs.studentAssessmentId.toString, Target.Declarations, Json.obj("acceptsAuthorship" -> decs.acceptsAuthorship.toString, "selfDeclaredRA" -> decs.selfDeclaredRA.toString, "completedRA" -> decs.completedRA.toString)) {
      daoRunner.run(dao.getDeclarations(decs.studentAssessmentId)).flatMap { result =>
        result.map { existingDeclaration =>
          daoRunner.run(dao.update(
            StoredDeclarations(
              studentAssessmentId = existingDeclaration.studentAssessmentId,
              acceptsAuthorship = decs.acceptsAuthorship,
              selfDeclaredRA = decs.selfDeclaredRA,
              completedRA = decs.completedRA,
              created = existingDeclaration.created,
              version = existingDeclaration.version
            )
          ))
        }.getOrElse {
          val timestamp = JavaTime.offsetDateTime
          daoRunner.run(dao.insert(StoredDeclarations(
            studentAssessmentId = decs.studentAssessmentId,
            acceptsAuthorship = decs.acceptsAuthorship,
            selfDeclaredRA = decs.selfDeclaredRA,
            completedRA = decs.completedRA,
            created = timestamp,
            version = timestamp
          )))
        }
          .map(_.asDeclarations)
          .map(ServiceResults.success)
      }
    }
  }


  private def noAssessmentFound(id: UUID) =
    throw new NoSuchElementException(s"Could not find an assessment with id ${id.toString}")

  private def noStudentAssessmentFound(assessmentId: UUID, studentId: UniversityID) =
    throw new NoSuchElementException(s"Could not find student assessment with id ${assessmentId.toString} and student id ${studentId.string}")
}

object StudentAssessmentService {
  def inflateRowsWithUploadedFiles(rows: Seq[(StudentAssessmentsTables.StoredStudentAssessment, Set[UploadedFilesTables.StoredUploadedFile])]): Seq[StudentAssessment] =
    rows.map { case (studentAssessment, storedUploadedFiles) =>
      studentAssessment.asStudentAssessment(
        storedUploadedFiles.map(f => f.id -> f.asUploadedFile).toMap
      )
    }

  def inflateRowWithUploadedFiles(row: Option[(StudentAssessmentsTables.StoredStudentAssessment, Set[UploadedFilesTables.StoredUploadedFile])]): Option[StudentAssessment] =
    inflateRowsWithUploadedFiles(row.toSeq).headOption
}
