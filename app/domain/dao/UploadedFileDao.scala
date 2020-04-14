package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.UploadedFilesTables._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

object UploadedFilesTables {
  case class StoredUploadedFile(
    id: UUID,
    fileName: String,
    contentLength: Long,
    contentType: String,
    uploadedBy: Usercode,
    uploadStarted: OffsetDateTime,
    ownerId: Option[UUID],
    ownerType: Option[UploadedFileOwner],
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredUploadedFile] {
    def asUploadedFile = UploadedFile(
      id,
      fileName,
      contentLength,
      contentType,
      uploadedBy,
      created,
      version,
      uploadStarted,
    )

    override def atVersion(at: OffsetDateTime): StoredUploadedFile = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredUploadedFile]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredUploadedFileVersion(
        id,
        fileName,
        contentLength,
        contentType,
        uploadedBy,
        uploadStarted,
        ownerId,
        ownerType,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  object StoredUploadedFile {
    def tupled = (apply _).tupled

    // oldest first
    val dateOrdering: Ordering[StoredUploadedFile] = Ordering.by[StoredUploadedFile, OffsetDateTime](data => data.created)(JavaTime.dateTimeOrdering)
  }

  case class StoredUploadedFileVersion(
    id: UUID,
    fileName: String,
    contentLength: Long,
    contentType: String,
    uploadedBy: Usercode,
    uploadStarted: OffsetDateTime,
    ownerId: Option[UUID],
    ownerType: Option[UploadedFileOwner],
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredUploadedFile]
}

@ImplementedBy(classOf[UploadedFileDaoImpl])
trait UploadedFileDao {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def allWithoutOwner: DBIO[Seq[StoredUploadedFile]]
  def find(id: UUID): DBIO[Option[StoredUploadedFile]]
  def find(ids: Seq[UUID]): DBIO[Seq[StoredUploadedFile]]
  def insert(file: StoredUploadedFile)(implicit ac: AuditLogContext): DBIO[StoredUploadedFile]
  def delete(id: UUID)(implicit ac: AuditLogContext): DBIO[Boolean]
}

@Singleton
class UploadedFileDaoImpl @Inject()(
  @NamedDatabase("default") protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
  tables: AssessmentTables,
)(implicit ec: ExecutionContext) extends UploadedFileDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import tables._

  override def allWithoutOwner: DBIO[Seq[StoredUploadedFile]] =
    uploadedFiles.table.filter(_.ownerId.isEmpty).result

  override def find(id: UUID): DBIO[Option[StoredUploadedFile]] =
    uploadedFiles.table.filter(_.id === id).result.headOption

  override def find(ids: Seq[UUID]): DBIO[Seq[StoredUploadedFile]] =
    uploadedFiles.table.filter(_.id inSetBind ids).result

  override def insert(file: StoredUploadedFile)(implicit ac: AuditLogContext): DBIO[StoredUploadedFile] =
    uploadedFiles.insert(file)

  override def delete(id: UUID)(implicit ac: AuditLogContext): DBIO[Boolean] =
    uploadedFiles.table.filter(_.id === id).delete.map(_ > 0)
}
