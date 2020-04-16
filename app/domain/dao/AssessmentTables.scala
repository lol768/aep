package domain.dao

import java.time.{Duration, OffsetDateTime, ZoneId}
import java.util.UUID

import domain.Assessment.{AssessmentType, DurationStyle, Platform, State}
import domain.dao.AssessmentsTables.{StoredAssessment, StoredAssessmentVersion, StoredBrief}
import domain.dao.StudentAssessmentsTables.{StoredDeclarations, StoredDeclarationsVersion, StoredStudentAssessment, StoredStudentAssessmentVersion}
import domain.dao.UploadedFilesTables.{StoredUploadedFile, StoredUploadedFileVersion}
import domain.{AssessmentClientNetworkActivity, DatabaseOperation, DepartmentCode, ExtendedPostgresProfile, PostgresCustomJdbcTypes, StoredVersionTable, UploadedFileOwner, VersionedTable, VersionedTables}
import helpers.LenientTimezoneNameParsing.LenientZoneId
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import shapeless._
import slickless._
import slick.lifted.ProvenShape
import warwick.sso.{UniversityID, Usercode}

@Singleton
class AssessmentTables @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
) extends HasDatabaseConfigProvider[ExtendedPostgresProfile] with VersionedTables {

  import profile.api._
  import jdbcTypes._

  trait AssessmentCommonProperties {
    self: Table[_] =>
    def paperCode = column[String]("paper_code")
    def section = column[Option[String]]("section")
    def title = column[String]("title")
    def startTime = column[Option[OffsetDateTime]]("start_time_utc")
    def duration = column[Option[Duration]]("duration")
    def platform = column[Set[Platform]]("platform")
    def assessmentType = column[Option[AssessmentType]]("type")
    def durationStyle = column[DurationStyle]("duration_style")
    def storedBrief = column[StoredBrief]("brief")
    def invigilators = column[List[String]]("invigilators")
    def state = column[State]("state")
    def tabulaAssessmentId = column[Option[UUID]]("tabula_assessment_id")
    def tabulaAssignments = column[List[String]]("tabula_assignments")
    def examProfileCode = column[String]("exam_profile_code")
    def moduleCode = column[String]("module_code")
    def departmentCode = column[DepartmentCode]("department_code")
    def sequence = column[String]("sequence")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Assessments(tag: Tag) extends Table[StoredAssessment](tag, "assessment")
    with VersionedTable[StoredAssessment]
    with AssessmentCommonProperties {
    override def matchesPrimaryKey(other: StoredAssessment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredAssessment] = (
      id ::
      paperCode ::
      section ::
      title ::
      startTime ::
      duration ::
      platform ::
      assessmentType ::
      durationStyle ::
      storedBrief ::
      invigilators ::
      state ::
      tabulaAssessmentId ::
      tabulaAssignments ::
      examProfileCode ::
      moduleCode ::
      departmentCode ::
      sequence ::
      created ::
      version ::
      HNil
    ).mappedWith(Generic[StoredAssessment])

    def paperCodeIndex = index("idx_assessment_papercode", (paperCode, section, examProfileCode))
    def tabulaAssessmentIndex = index("idx_assessment_tabula", (tabulaAssessmentId, examProfileCode), unique = true)
  }

  class AssessmentVersions(tag: Tag) extends Table[StoredAssessmentVersion](tag, "assessment_version")
    with StoredVersionTable[StoredAssessment]
    with AssessmentCommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredAssessmentVersion] = (
      id ::
      paperCode ::
      section ::
      title ::
      startTime ::
      duration ::
      platform ::
      assessmentType ::
      durationStyle ::
      storedBrief ::
      invigilators ::
      state ::
      tabulaAssessmentId ::
      tabulaAssignments ::
      examProfileCode ::
      moduleCode ::
      departmentCode ::
      sequence ::
      created ::
      version ::
      operation ::
      timestamp ::
      auditUser ::
      HNil
    ).mappedWith(Generic[StoredAssessmentVersion])

    def pk = primaryKey("pk_assessment_version", (id, timestamp))
  }

  implicit class AssessmentExtensions[C[_]](q: Query[Assessments, StoredAssessment, C]) {
    def withUploadedFiles = q
      .joinLeft(uploadedFiles.table)
      .on { case (a, f) =>
        a.id === f.ownerId && f.ownerType === (UploadedFileOwner.AssessmentBrief: UploadedFileOwner) && (a.storedBrief.asColumnOf[JsValue] +> "fileIds") ?? f.id.asColumnOf[String]
      }
  }

  val assessments: VersionedTableQuery[StoredAssessment, StoredAssessmentVersion, Assessments, AssessmentVersions] =
    VersionedTableQuery(TableQuery[Assessments], TableQuery[AssessmentVersions])

  trait StudentAssessmentCommonProperties { self: Table[_] =>
    def assessmentId = column[UUID]("assessment_id")
    def studentId = column[UniversityID]("student_id")
    def inSeat = column[Boolean]("in_seat")
    def startTime = column[Option[OffsetDateTime]]("start_time_utc")
    def extraTimeAdjustment = column[Option[Duration]]("extra_time_adjustment")
    def finaliseTime = column[Option[OffsetDateTime]]("finalise_time_utc")
    def uploadedFiles = column[List[UUID]]("uploaded_file_ids")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }
  class StudentAssessments(tag: Tag) extends Table[StoredStudentAssessment](tag, "student_assessment")
    with VersionedTable[StoredStudentAssessment]
    with StudentAssessmentCommonProperties {
    override def matchesPrimaryKey(other: StoredStudentAssessment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    def pk = primaryKey("pk_student_assessment", id)
    def ck = index("ck_student_assessment", (assessmentId, studentId), unique = true)

    override def * : ProvenShape[StoredStudentAssessment] =
      (id, assessmentId, studentId, inSeat, startTime, extraTimeAdjustment, finaliseTime, uploadedFiles, created, version).mapTo[StoredStudentAssessment]
  }

  class StudentAssessmentVersions(tag: Tag) extends Table[StoredStudentAssessmentVersion](tag, "student_assessment_version")
    with StoredVersionTable[StoredStudentAssessment]
    with StudentAssessmentCommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredStudentAssessmentVersion] =
      (id, assessmentId, studentId, inSeat, startTime, extraTimeAdjustment, finaliseTime, uploadedFiles, created, version, operation, timestamp, auditUser).mapTo[StoredStudentAssessmentVersion]
    def pk = primaryKey("pk_student_assessment_version", (assessmentId, studentId, timestamp))
  }

  implicit class StudentAssessmentExtensions[C[_]](q: Query[StudentAssessments, StoredStudentAssessment, C]) {
    def withUploadedFiles = q
      .joinLeft(uploadedFiles.table)
      .on { case (a, f) =>
        a.id === f.ownerId && f.ownerType === (UploadedFileOwner.StudentAssessment: UploadedFileOwner) && f.id === a.uploadedFiles.any
      }
  }

  val studentAssessments: VersionedTableQuery[StoredStudentAssessment, StoredStudentAssessmentVersion, StudentAssessments, StudentAssessmentVersions] =
    VersionedTableQuery(TableQuery[StudentAssessments], TableQuery[StudentAssessmentVersions])

  trait UploadedFileCommonProperties { self: Table[_] =>
    def fileName = column[String]("file_name")
    def contentLength = column[Long]("content_length")
    def contentType = column[String]("content_type")
    def uploadedBy = column[Usercode]("uploaded_by")
    def uploadStarted = column[OffsetDateTime]("upload_started_utc")
    def ownerId = column[Option[UUID]]("owner_id")
    def ownerType = column[Option[UploadedFileOwner]]("owner_type")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class UploadedFiles(tag: Tag) extends Table[StoredUploadedFile](tag, "uploaded_file")
    with VersionedTable[StoredUploadedFile]
    with UploadedFileCommonProperties {
    override def matchesPrimaryKey(other: StoredUploadedFile): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredUploadedFile] =
      (id, fileName, contentLength, contentType, uploadedBy, uploadStarted, ownerId, ownerType, created, version).mapTo[StoredUploadedFile]
  }

  class UploadedFileVersions(tag: Tag) extends Table[StoredUploadedFileVersion](tag, "uploaded_file_version")
    with StoredVersionTable[StoredUploadedFile]
    with UploadedFileCommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredUploadedFileVersion] =
      (id, fileName, contentLength, contentType, uploadedBy, uploadStarted, ownerId, ownerType, created, version, operation, timestamp, auditUser).mapTo[StoredUploadedFileVersion]
    def pk = primaryKey("pk_uploaded_file_version", (id, timestamp))
    def idx = index("idx_uploaded_file_version", (id, version))
  }

  val uploadedFiles: VersionedTableQuery[StoredUploadedFile, StoredUploadedFileVersion, UploadedFiles, UploadedFileVersions] =
    VersionedTableQuery(TableQuery[UploadedFiles], TableQuery[UploadedFileVersions])

  class AssessmentClientNetworkActivities(tag: Tag) extends Table[AssessmentClientNetworkActivity](tag, "assessment_client_network_activity") {
    def downlink = column[Option[Double]]("downlink")
    def downlinkMax = column[Option[Double]]("downlink_max")
    def effectiveType = column[Option[String]]("effective_type")
    def rtt = column[Option[Int]]("rtt")
    def `type` = column[Option[String]]("type")
    def studentAssessmentId = column[UUID]("student_assessment_id")
    def localTimezoneName = column[Option[LenientZoneId]]("local_timezone_name")
    def timestamp = column[OffsetDateTime]("timestamp_utc")

    def * = {
      (downlink, downlinkMax, effectiveType, rtt, `type`, studentAssessmentId, localTimezoneName, timestamp).mapTo[AssessmentClientNetworkActivity]
    }
  }

  val assessmentClientNetworkActivities = TableQuery[AssessmentClientNetworkActivities]

  trait DeclarationsCommonProperties { self: Table[_] =>
    def acceptsAuthorship = column[Boolean]("accepts_authorship")
    def selfDeclaredRA = column[Boolean]("self_declared_ra")
    def completedRADeclaration = column[Boolean]("completed_ra")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }
  class Declarations(tag: Tag) extends Table[StoredDeclarations](tag, "declarations")
    with VersionedTable[StoredDeclarations]
    with DeclarationsCommonProperties {
    override def matchesPrimaryKey(other: StoredDeclarations): Rep[Boolean] = studentAssessmentId === other.studentAssessmentId
    def studentAssessmentId = column[UUID]("id", O.PrimaryKey)

    def pk = primaryKey("pk_declaration", studentAssessmentId)

    override def * : ProvenShape[StoredDeclarations] =
      (studentAssessmentId, acceptsAuthorship, selfDeclaredRA, completedRADeclaration, created, version).mapTo[StoredDeclarations]
  }

  class DeclarationsVersions(tag: Tag) extends Table[StoredDeclarationsVersion](tag, "declarations_version")
    with StoredVersionTable[StoredDeclarations]
    with DeclarationsCommonProperties {
    def studentAssessmentId = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredDeclarationsVersion] =
      (studentAssessmentId, acceptsAuthorship, selfDeclaredRA, completedRADeclaration, created, version, operation, timestamp, auditUser).mapTo[StoredDeclarationsVersion]
    def pk = primaryKey("pk_declarations_version", (studentAssessmentId, timestamp))
  }

  val declarations: VersionedTableQuery[StoredDeclarations, StoredDeclarationsVersion, Declarations, DeclarationsVersions] =
    VersionedTableQuery(TableQuery[Declarations], TableQuery[DeclarationsVersions])
}
