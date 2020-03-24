package domain.dao

import java.time.{Duration, OffsetDateTime, ZoneId}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment._
import domain._
import domain.dao.AssessmentsTables.{StoredAssessment, StoredAssessmentVersion}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{Format, JsValue, Json}
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
    def jsonBrief = column[JsValue]("brief")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }
  class Assessments(tag: Tag) extends Table[StoredAssessment](tag, "assessment")
    with VersionedTable[StoredAssessment]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredAssessment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAssessment] =
      (id, code, title, startTime, duration, platform, assessmentType, jsonBrief, created, version) <> ((StoredAssessment.mapper _).tupled, StoredAssessment.unapply)

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
      (id, code, title, startTime, duration, platform, assessmentType, jsonBrief, created, version, operation, timestamp, auditUser).mapTo[StoredAssessmentVersion]
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
    jsonBrief: JsValue,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredAssessment] {

    def storedBrief: StoredBrief =
      jsonBrief.validate[StoredBrief](StoredBrief.format).getOrElse(StoredBrief.empty)

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

    def asAssessmentMetadata =
      AssessmentMetadata(
        id,
        code,
        title,
        startTime,
        duration,
        platform,
        assessmentType
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
        jsonBrief,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  object StoredAssessment {
    // for testing
    def apply(
      id: UUID,
      code: String,
      title: String,
      startTime: Option[OffsetDateTime],
      duration: Duration,
      platform: Platform,
      assessmentType: AssessmentType,
      storedBrief: StoredBrief,
      created: OffsetDateTime,
      version: OffsetDateTime
    ): StoredAssessment =
      StoredAssessment(
        id,
        code,
        title,
        startTime,
        duration,
        platform,
        assessmentType,
        Json.toJson(storedBrief)(StoredBrief.format),
        created,
        version
      )

    // ugh, such boilerplate
    // it's needed because this companion object would shadow the case class when tupled is called by slick's .mapTo[]
    def mapper(
      id: UUID,
      code: String,
      title: String,
      startTime: Option[OffsetDateTime],
      duration: Duration,
      platform: Platform,
      assessmentType: AssessmentType,
      jsonBrief: JsValue,
      created: OffsetDateTime,
      version: OffsetDateTime
    ): StoredAssessment = apply(
      id,
      code,
      title,
      startTime,
      duration,
      platform,
      assessmentType,
      jsonBrief,
      created,
      version
    )
  }

  case class StoredAssessmentVersion(
    id: UUID = UUID.randomUUID(),
    code: String,
    title: String,
    startTime: Option[OffsetDateTime],
    duration: Duration,
    platform: Platform,
    assessmentType: AssessmentType,
    jsonBrief: JsValue,
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
    implicit val format: Format[StoredBrief] = Json.format[StoredBrief]
    def empty: StoredBrief = StoredBrief(None, Seq.empty, None)
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
  def getToday: DBIO[Seq[StoredAssessment]]
  def getInWindow: DBIO[Seq[StoredAssessment]]
}

@Singleton
class AssessmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: CustomJdbcTypes
)(implicit ec: ExecutionContext) extends AssessmentDao with AssessmentsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._

  lazy val warwickTz = ZoneId.of("Europe/London")

  override def all: DBIO[Seq[StoredAssessment]] = assessments.result

  override def insert(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment] =
    assessments.insert(assessment)

  override def getById(id: UUID): DBIO[StoredAssessment] =
    assessments.table.filter(_.id === id).result.head

  override def getByIds(ids: Seq[UUID]): DBIO[Seq[StoredAssessment]] =
    assessments.table.filter(_.id inSetBind ids).result

  override def getByCode(code: String): DBIO[StoredAssessment] =
    assessments.table.filter(_.code === code).result.head

  override def getToday: DBIO[Seq[StoredAssessment]] = {
    val today = OffsetDateTime.now.toLocalDate.atStartOfDay(warwickTz).toOffsetDateTime
    assessments.table.filter(a => a.startTime >= today && a.startTime < today.plusDays(1)).result
  }

  override def getInWindow: DBIO[Seq[StoredAssessment]] = {
    val now = OffsetDateTime.now
    assessments.table.filter(a => a.startTime < now && a.startTime < now.minus(Assessment.window)).result
  }

}
