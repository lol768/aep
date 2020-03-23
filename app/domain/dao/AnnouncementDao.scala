package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.AnnouncementsTables.{StoredAnnouncement, StoredAnnouncementVersion}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.lifted.ProvenShape
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait AnnouncementsTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: CustomJdbcTypes
  import jdbcTypes._

  trait CommonProperties { self: Table[_] =>
    def assessmentId = column[UUID]("assessment_id")
    def text = column[String]("text")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }
  class Announcements(tag: Tag) extends Table[StoredAnnouncement](tag, "announcement")
    with VersionedTable[StoredAnnouncement]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredAnnouncement): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAnnouncement] =
      (id, assessmentId, text, created, version).mapTo[StoredAnnouncement]
  }

  class AnnouncementVersions(tag: Tag) extends Table[StoredAnnouncementVersion](tag, "announcement_version")
    with StoredVersionTable[StoredAnnouncement]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAnnouncementVersion] =
      (id, assessmentId, text, created, version, operation, timestamp, auditUser).mapTo[StoredAnnouncementVersion]

    def pk = primaryKey("pk_announcement_version", (id, timestamp))
  }

  val announcements: VersionedTableQuery[StoredAnnouncement, StoredAnnouncementVersion, Announcements, AnnouncementVersions] =
    VersionedTableQuery(TableQuery[Announcements], TableQuery[AnnouncementVersions])
}

object AnnouncementsTables {
  case class StoredAnnouncement(
    id: UUID = UUID.randomUUID(),
    assessmentId: UUID,
    text: String,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredAnnouncement] {
    def asAnnouncement: Announcement =
      Announcement(
        id,
        assessmentId,
        text,
      )
    override def atVersion(at: OffsetDateTime): StoredAnnouncement = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAnnouncement]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAnnouncementVersion(
        id,
        assessmentId,
        text,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredAnnouncementVersion(
    id: UUID = UUID.randomUUID(),
    assessmentId: UUID,
    text: String,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredAnnouncement]
}


@ImplementedBy(classOf[AnnouncementDaoImpl])
trait AnnouncementDao {
  self: AnnouncementsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredAnnouncement]]
  def insert(announcement: StoredAnnouncement)(implicit ac: AuditLogContext): DBIO[StoredAnnouncement]
  def getById(id: UUID): DBIO[StoredAnnouncement]
  def getByAssessmentId(id: UUID): DBIO[Seq[StoredAnnouncement]]
}

@Singleton
class AnnouncementDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: CustomJdbcTypes
)(implicit ec: ExecutionContext) extends AnnouncementDao with AnnouncementsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._

  override def all: DBIO[Seq[StoredAnnouncement]] = announcements.result

  override def insert(announcement: StoredAnnouncement)(implicit ac: AuditLogContext): DBIO[StoredAnnouncement] =
    announcements.insert(announcement)

  override def getById(id: UUID): DBIO[StoredAnnouncement] =
    announcements.table.filter(_.id === id).result.head

  override def getByAssessmentId(id: UUID): DBIO[Seq[StoredAnnouncement]] =
    announcements.table.filter(_.assessmentId === id).result
}
