package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain._
import domain.dao.UploadedFilesTables._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import slick.lifted.ProvenShape
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait UploadedFilesTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: PostgresCustomJdbcTypes
  import jdbcTypes._

  trait CommonProperties { self: Table[_] =>
    def fileName = column[String]("file_name")
    def contentLength = column[Long]("content_length")
    def contentType = column[String]("content_type")
    def uploadedBy = column[Usercode]("uploaded_by")
    def ownerId = column[Option[UUID]]("owner_id")
    def ownerType = column[Option[UploadedFileOwner]]("owner_type")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class UploadedFiles(tag: Tag) extends Table[StoredUploadedFile](tag, "uploaded_file")
    with VersionedTable[StoredUploadedFile]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredUploadedFile): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredUploadedFile] =
      (id, fileName, contentLength, contentType, uploadedBy, ownerId, ownerType, created, version).mapTo[StoredUploadedFile]
  }

  class UploadedFileVersions(tag: Tag) extends Table[StoredUploadedFileVersion](tag, "uploaded_file_version")
    with StoredVersionTable[StoredUploadedFile]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredUploadedFileVersion] =
      (id, fileName, contentLength, contentType, uploadedBy, ownerId, ownerType, created, version, operation, timestamp, auditUser).mapTo[StoredUploadedFileVersion]
    def pk = primaryKey("pk_uploaded_file_version", (id, timestamp))
    def idx = index("idx_uploaded_file_version", (id, version))
  }

  val uploadedFiles: VersionedTableQuery[StoredUploadedFile, StoredUploadedFileVersion, UploadedFiles, UploadedFileVersions] =
    VersionedTableQuery(TableQuery[UploadedFiles], TableQuery[UploadedFileVersions])
}

object UploadedFilesTables {
  case class StoredUploadedFile(
    id: UUID,
    fileName: String,
    contentLength: Long,
    contentType: String,
    uploadedBy: Usercode,
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
      version
    )

    override def atVersion(at: OffsetDateTime): StoredUploadedFile = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredUploadedFile]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredUploadedFileVersion(
        id,
        fileName,
        contentLength,
        contentType,
        uploadedBy,
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
  self: UploadedFilesTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredUploadedFile]]
  def find(id: UUID): DBIO[Option[StoredUploadedFile]]
  def find(ids: Seq[UUID]): DBIO[Seq[StoredUploadedFile]]
  def insert(file: StoredUploadedFile)(implicit ac: AuditLogContext): DBIO[StoredUploadedFile]
  def delete(file: StoredUploadedFile)(implicit ac: AuditLogContext): DBIO[Done]
}

@Singleton
class UploadedFileDaoImpl @Inject()(
  @NamedDatabase("default") protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
)(implicit ec: ExecutionContext) extends UploadedFileDao with UploadedFilesTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  import dbConfig.profile.api._

  override def all: DBIO[Seq[StoredUploadedFile]] =
    uploadedFiles.result

  override def find(id: UUID): DBIO[Option[StoredUploadedFile]] =
    uploadedFiles.table.filter(_.id === id).result.headOption

  override def find(ids: Seq[UUID]): DBIO[Seq[StoredUploadedFile]] =
    uploadedFiles.table.filter(_.id inSetBind ids).result

  override def insert(file: StoredUploadedFile)(implicit ac: AuditLogContext): DBIO[StoredUploadedFile] =
    uploadedFiles.insert(file)

  override def delete(file: StoredUploadedFile)(implicit ac: AuditLogContext): DBIO[Done] =
    uploadedFiles.delete(file)

}
