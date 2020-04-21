package services

import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.ExtendedPostgresProfile.api._
import domain.UploadedFileOwner
import domain.dao.UploadedFilesTables.StoredUploadedFile
import domain.dao.{DaoRunner, UploadedFileDao}
import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.Json
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.{TimingCategories, TimingContext, TimingService}
import warwick.fileuploads.{UploadedFile, UploadedFileSave}
import warwick.objectstore.ObjectStorageService
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UploadedFileServiceImpl])
trait UploadedFileService {
  def listWithoutOwner()(implicit t: TimingContext): Future[ServiceResult[Seq[UploadedFile]]]
  def listWithOwner(ownerId: UUID, ownerType: UploadedFileOwner)(implicit t: TimingContext): Future[ServiceResult[Seq[UploadedFile]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[UploadedFile]]
  def get(ids: Seq[UUID])(implicit t: TimingContext): Future[Seq[UploadedFile]]

  def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode)(implicit ac: AuditLogContext): DBIO[UploadedFile]
  def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode, ownerId: UUID, ownerType: UploadedFileOwner)(implicit ac: AuditLogContext): DBIO[UploadedFile]
  def store(in: ByteSource, metadata: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]]
  def store(in: ByteSource, metadata: UploadedFileSave, ownerId: UUID, ownerType: UploadedFileOwner)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]]

  def deleteDBIO(id: UUID)(implicit ac: AuditLogContext): DBIO[Done]
  def delete(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class UploadedFileServiceImpl @Inject()(
  auditService: AuditService,
  objectStorageService: ObjectStorageService,
  daoRunner: DaoRunner,
  dao: UploadedFileDao,
  timing: TimingService,
  @Named("fileUploadsExecutionContext") objectStorageExecutionContext: ExecutionContext,
)(implicit ec: ExecutionContext) extends UploadedFileService {

  import timing._

  override def listWithoutOwner()(implicit t: TimingContext): Future[ServiceResult[Seq[UploadedFile]]] =
    daoRunner.run(dao.allWithoutOwner).map { files => ServiceResults.success(files.map(_.asUploadedFile)) }

  override def listWithOwner(ownerId: UUID, ownerType: UploadedFileOwner)(implicit t: TimingContext): Future[ServiceResult[Seq[UploadedFile]]] =
    daoRunner.run(dao.findByOwner(ownerId, ownerType)).map { files => ServiceResults.success(files.map(_.asUploadedFile)) }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[UploadedFile]] =
    daoRunner.run(dao.find(id).map{ _.map { f =>
      ServiceResults.success(f.asUploadedFile)
    }.getOrElse{
      ServiceResults.error(s"Could not find an UploadedFile with ID $id")
    }})

  override def get(ids: Seq[UUID])(implicit t: TimingContext): Future[Seq[UploadedFile]] =
    daoRunner.run(dao.find(ids)).map { files =>
      files.map(_.asUploadedFile)
    }

  private def putInObjectStorage(id: UUID, in: ByteSource, metadata: UploadedFileSave)(implicit t: TimingContext): Future[Unit] =
    time(TimingCategories.ObjectStorageWrite) {
      Future {
        objectStorageService.put(id.toString, in, ObjectStorageService.Metadata(
          contentLength = metadata.contentLength,
          contentType = metadata.contentType,
          fileHash = None, // This is calculated and stored by EncryptedObjectStorageService so no need to do it here
        ))
      }(objectStorageExecutionContext)
    }

  private def insertDBIO(id: UUID, metadata: UploadedFileSave, uploader: Usercode, ownerId: Option[UUID], ownerType: Option[UploadedFileOwner])(implicit ac: AuditLogContext): DBIO[StoredUploadedFile] =
    dao.insert(StoredUploadedFile(
      id,
      metadata.fileName,
      metadata.contentLength,
      metadata.contentType,
      uploader,
      metadata.uploadStarted,
      ownerId,
      ownerType,
      JavaTime.offsetDateTime,
      JavaTime.offsetDateTime
    ))

  private def storeDBIOInTransaction(id: UUID, in: ByteSource, metadata: UploadedFileSave, uploader: Usercode, ownerId: Option[UUID], ownerType: Option[UploadedFileOwner])(implicit ac: AuditLogContext): DBIO[UploadedFile] = {
    for {
      // Treat the ObjectStorageService put as DBIO so we force a rollback if it fails (even though it won't delete the object)
      _ <- DBIO.from(putInObjectStorage(id, in, metadata))
      file <- insertDBIO(id, metadata, uploader, ownerId, ownerType)
    } yield file.asUploadedFile
  }

  override def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode)(implicit ac: AuditLogContext): DBIO[UploadedFile] =
    storeDBIOInTransaction(UUID.randomUUID(), in, metadata, uploader, None, None)

  override def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode, ownerId: UUID, ownerType: UploadedFileOwner)(implicit ac: AuditLogContext): DBIO[UploadedFile] =
    storeDBIOInTransaction(UUID.randomUUID(), in, metadata, uploader, Some(ownerId), Some(ownerType))

  override def store(in: ByteSource, metadata: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]] =
    auditService.audit[UploadedFile](Operation.UploadedFile.Save, (f: UploadedFile) => f.id.toString, Target.UploadedFile, Json.obj()) {
      // Don't do the store in the transaction so don't delegate to storeDBIO
      val id = UUID.randomUUID()
      putInObjectStorage(id, in, metadata).flatMap { _ =>
        daoRunner.run(insertDBIO(id, metadata, ac.usercode.get, None, None)).map(suf => ServiceResults.success(suf.asUploadedFile))
      }
    }

  override def store(in: ByteSource, metadata: UploadedFileSave, ownerId: UUID, ownerType: UploadedFileOwner)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]] =
    auditService.audit[UploadedFile](Operation.UploadedFile.Save, (f: UploadedFile) => f.id.toString, Target.UploadedFile, Json.obj()) {
      // Don't do the store in the transaction so don't delegate to storeDBIO
      val id = UUID.randomUUID()
      putInObjectStorage(id, in, metadata).flatMap { _ =>
        daoRunner.run(insertDBIO(id, metadata, ac.usercode.get, Some(ownerId), Some(ownerType))).map(suf => ServiceResults.success(suf.asUploadedFile))
      }
    }

  override def deleteDBIO(id: UUID)(implicit ac: AuditLogContext): DBIO[Done] = {
    dao.delete(id).map {
      case true => Done
      case false => throw new NoSuchElementException(s"Could not find file with id ${id.toString}")
    }
  }

  override def delete(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.UploadedFile.Delete, id.toString, Target.UploadedFile, Json.obj()) {
      daoRunner.run(deleteDBIO(id)).map(Right.apply)
    }

}
