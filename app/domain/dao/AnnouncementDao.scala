package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.AnnouncementsTables.{StoredAnnouncement, StoredAnnouncementVersion}
import domain.dao.AssessmentsTables.StoredAssessment
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.lifted.ProvenShape
import warwick.core.system.AuditLogContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

trait AnnouncementsTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: PostgresCustomJdbcTypes
  import jdbcTypes._

  trait CommonProperties { self: Table[_] =>
    def assessmentId = column[UUID]("assessment_id")
    def sender = column[Option[Usercode]]("sender")
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
      (id, sender, assessmentId, text, created, version).mapTo[StoredAnnouncement]
  }

  class AnnouncementVersions(tag: Tag) extends Table[StoredAnnouncementVersion](tag, "announcement_version")
    with StoredVersionTable[StoredAnnouncement]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAnnouncementVersion] =
      (id, sender, assessmentId, text, created, version, operation, timestamp, auditUser).mapTo[StoredAnnouncementVersion]

    def pk = primaryKey("pk_announcement_version", (id, timestamp))
  }

  val announcements: VersionedTableQuery[StoredAnnouncement, StoredAnnouncementVersion, Announcements, AnnouncementVersions] =
    VersionedTableQuery(TableQuery[Announcements], TableQuery[AnnouncementVersions])
}

object AnnouncementsTables {
  case class StoredAnnouncement(
    id: UUID = UUID.randomUUID(),
    sender: Option[Usercode],
    assessmentId: UUID,
    text: String,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredAnnouncement] {
    def asAnnouncement: Announcement =
      Announcement(
        id,
        sender,
        assessmentId,
        text,
        created,
      )
    override def atVersion(at: OffsetDateTime): StoredAnnouncement = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAnnouncement]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAnnouncementVersion(
        id,
        sender,
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
    sender: Option[Usercode],
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
  def delete(id: UUID)(implicit ac: AuditLogContext): DBIO[Int]
  def getById(id: UUID): DBIO[Option[StoredAnnouncement]]
  def getByAssessmentId(id: UUID): DBIO[Seq[StoredAnnouncement]]
  def getByAssessmentId(student: UniversityID, id: UUID): DBIO[Seq[StoredAnnouncement]]
  def getByDepartmentCode(departmentCode: DepartmentCode): DBIO[Seq[(StoredAssessment, StoredAnnouncement)]]
}

@Singleton
class AnnouncementDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
  assessmentTables: AssessmentTables,
)(implicit ec: ExecutionContext) extends AnnouncementDao with AnnouncementsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import jdbcTypes._
  import assessmentTables.studentAssessments

  override def all: DBIO[Seq[StoredAnnouncement]] = announcements.result

  override def insert(announcement: StoredAnnouncement)(implicit ac: AuditLogContext): DBIO[StoredAnnouncement] =
    announcements.insert(announcement)

  override def delete(id: UUID)(implicit ac: AuditLogContext): DBIO[Int] =
    for {
      a <- announcements.table.filter(_.id === id).result.headOption
      rows <- a match {
        case Some(announcement) => announcements.delete(announcement).map(_ => 1)
        case _ => DBIO.successful(0)
      }
    } yield rows

  override def getById(id: UUID): DBIO[Option[StoredAnnouncement]] =
    announcements.table.filter(_.id === id).result.headOption

  override def getByAssessmentId(id: UUID): DBIO[Seq[StoredAnnouncement]] =
    announcements.table.filter(_.assessmentId === id).result

  def getByAssessmentId(student: UniversityID, id: UUID): DBIO[Seq[StoredAnnouncement]] =
    announcements.table.filter(_.assessmentId === id)
      .join(studentAssessments.table.filter(_.studentId === student))
      .on((a, sa) => a.assessmentId === sa.assessmentId)
      .map { case (a, _) => a }
      .result

  def getByDepartmentCode(departmentCode: DepartmentCode): DBIO[Seq[(StoredAssessment, StoredAnnouncement)]] =
    assessmentTables.assessments.table.filter(_.departmentCode === departmentCode)
      .join(announcements.table)
      .on((assessment, announcement) => announcement.assessmentId === assessment.id)
      .result
}
