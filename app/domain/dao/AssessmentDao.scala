package domain.dao

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment._
import domain._
import domain.dao.AssessmentsTables.{StoredAssessment, StoredAssessmentVersion, StoredBrief}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{Json, OFormat}
import slick.lifted.ProvenShape
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait AssessmentsTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: CustomJdbcTypes
  import jdbcTypes._

  trait CommonProperties { self: Table[_] =>
    def code = column[String]("code")
    def title = column[String]("title")
    def startTime = column[Option[OffsetDateTime]]("start_time_utc")
    def duration = column[Duration]("duration")
    def platform = column[Platform]("platform")
    def assessmentType = column[AssessmentType]("type")
    def storedBrief = column[StoredBrief]("brief")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }
  class Assessments(tag: Tag) extends Table[StoredAssessment](tag, "assessment")
    with VersionedTable[StoredAssessment]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredAssessment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAssessment] =
      (id, code, title, startTime, duration, platform, assessmentType, storedBrief, created, version).mapTo[StoredAssessment]

    def idx = index("id_assessment_code", (code))
  }

  class AssessmentVersions(tag: Tag) extends Table[StoredAssessmentVersion](tag, "assessment_version")
    with StoredVersionTable[StoredAssessment]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAssessmentVersion] =
      (id, code, title, startTime, duration, platform, assessmentType, storedBrief, created, version, operation, timestamp, auditUser).mapTo[StoredAssessmentVersion]
    def pk = primaryKey("pk_assessment_version", (id, timestamp))
  }

  val assessments: VersionedTableQuery[StoredAssessment, StoredAssessmentVersion, Assessments, AssessmentVersions] =
    VersionedTableQuery(TableQuery[Assessments], TableQuery[AssessmentVersions])
}

object AssessmentsTables {
  case class StoredAssessment(
    id: UUID = UUID.randomUUID(),
    code: String,
    title: String,
    startTime: Option[OffsetDateTime],
    duration: Duration,
    platform: Platform,
    assessmentType: AssessmentType,
    storedBrief: StoredBrief,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredAssessment] {
    def asAssessment(fileMap: Map[UUID, UploadedFile]) =
      Assessment(
        id,
        code,
        title,
        startTime,
        duration,
        platform,
        assessmentType,
        storedBrief.asBrief(fileMap)
      )
    override def atVersion(at: OffsetDateTime): StoredAssessment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAssessment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAssessmentVersion(
        id,
        code,
        title,
        startTime,
        duration,
        platform,
        assessmentType,
        storedBrief,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredAssessmentVersion(
    id: UUID = UUID.randomUUID(),
    code: String,
    title: String,
    startTime: Option[OffsetDateTime],
    duration: Duration,
    platform: Platform,
    assessmentType: AssessmentType,
    storedBrief: StoredBrief,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredAssessment]

  case class StoredBrief(
    text: Option[String],
    fileIds: Seq[UUID],
    url: Option[String],
  ) {
    def asBrief(fileMap: Map[UUID, UploadedFile]) =
      Brief(
        text,
        fileIds.map(fileMap),
        url
      )
  }

  object StoredBrief {
    implicit val format: OFormat[StoredBrief] = Json.format[StoredBrief]
  }

}


@ImplementedBy(classOf[AssessmentDaoImpl])
trait AssessmentDao {
  self: AssessmentsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredAssessment]]
  def insert(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment]
  def getById(id: UUID): DBIO[StoredAssessment]
  def getByIds(ids: Seq[UUID]): DBIO[Seq[StoredAssessment]]
  def getByCode(code: String): DBIO[StoredAssessment]
}

@Singleton
class AssessmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: CustomJdbcTypes
)(implicit ec: ExecutionContext) extends AssessmentDao with AssessmentsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._

  override def all: DBIO[Seq[StoredAssessment]] = assessments.result

  override def insert(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment] =
    assessments.insert(assessment)

  override def getById(id: UUID): DBIO[StoredAssessment] =
    assessments.table.filter(_.id === id).result.head

  override def getByIds(ids: Seq[UUID]): DBIO[Seq[StoredAssessment]] =
    assessments.table.filter(_.id inSetBind ids).result

  override def getByCode(code: String): DBIO[StoredAssessment] =
    assessments.table.filter(_.code === code).result.head
}
