package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.StudentAssessmentsTables.{StoredStudentAssessment, StoredStudentAssessmentVersion}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.lifted.ProvenShape
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

trait StudentAssessmentsTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: PostgresCustomJdbcTypes
  import jdbcTypes._

  trait CommonProperties { self: Table[_] =>
    def assessmentId = column[UUID]("assessment_id")
    def studentId = column[UniversityID]("student_id")
    def inSeat = column[Boolean]("in_seat")
    def startTime = column[Option[OffsetDateTime]]("start_time_utc")
    def finaliseTime = column[Option[OffsetDateTime]]("finalise_time_utc")
    def uploadedFiles = column[List[UUID]]("uploaded_file_ids")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }
  class StudentAssessments(tag: Tag) extends Table[StoredStudentAssessment](tag, "student_assessment")
    with VersionedTable[StoredStudentAssessment]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredStudentAssessment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    def pk = primaryKey("pk_student_assessment", id)
    def ck = index("ck_student_assessment", (assessmentId, studentId), unique = true)

    override def * : ProvenShape[StoredStudentAssessment] =
      (id, assessmentId, studentId, inSeat, startTime, finaliseTime, uploadedFiles, created, version).mapTo[StoredStudentAssessment]
  }

  class StudentAssessmentVersions(tag: Tag) extends Table[StoredStudentAssessmentVersion](tag, "student_assessment_version")
    with StoredVersionTable[StoredStudentAssessment]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredStudentAssessmentVersion] =
      (id, assessmentId, studentId, inSeat, startTime, finaliseTime, uploadedFiles, created, version, operation, timestamp, auditUser).mapTo[StoredStudentAssessmentVersion]
    def pk = primaryKey("pk_student_assessment_version", (assessmentId, studentId, timestamp))
  }

  val studentAssessments: VersionedTableQuery[StoredStudentAssessment, StoredStudentAssessmentVersion, StudentAssessments, StudentAssessmentVersions] =
    VersionedTableQuery(TableQuery[StudentAssessments], TableQuery[StudentAssessmentVersions])
}

object StudentAssessmentsTables {
  case class StoredStudentAssessment(
    id: UUID,
    assessmentId: UUID,
    studentId: UniversityID,
    inSeat: Boolean,
    startTime: Option[OffsetDateTime],
    finaliseTime: Option[OffsetDateTime],
    uploadedFiles: List[UUID],
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredStudentAssessment] {
    def asStudentAssessment(fileMap: Map[UUID, UploadedFile]): StudentAssessment =
      StudentAssessment(
        id,
        assessmentId,
        studentId,
        inSeat,
        startTime,
        finaliseTime,
        uploadedFiles.map(fileMap)
      )

    def asStudentAssessmentMetadata =
      StudentAssessmentMetadata(
        assessmentId,
        studentId,
        inSeat,
        startTime,
        finaliseTime,
        uploadedFiles.length
      )

    override def atVersion(at: OffsetDateTime): StoredStudentAssessment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredStudentAssessment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredStudentAssessmentVersion(
        id,
        assessmentId,
        studentId,
        inSeat,
        startTime,
        finaliseTime,
        uploadedFiles,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredStudentAssessmentVersion(
    id: UUID,
    assessmentId: UUID,
    studentId: UniversityID,
    inSeat: Boolean,
    startTime: Option[OffsetDateTime],
    finaliseTime: Option[OffsetDateTime],
    uploadedFiles: List[UUID],
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredStudentAssessment]
}


@ImplementedBy(classOf[StudentAssessmentDaoImpl])
trait StudentAssessmentDao {
  self: StudentAssessmentsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredStudentAssessment]]
  def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]]
  def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]]
  def get(studentId: UniversityID, assessmentId: UUID): DBIO[StoredStudentAssessment]
}

@Singleton
class StudentAssessmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes
)(implicit ec: ExecutionContext) extends StudentAssessmentDao with StudentAssessmentsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import jdbcTypes._

  override def all: DBIO[Seq[StoredStudentAssessment]] = studentAssessments.result

  override def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.insert(assessment)

  override def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.update(studentAssessment)

  override def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]] =
    studentAssessments.table.filter(_.studentId === studentId).result

  override def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]] =
    studentAssessments.table.filter(_.assessmentId === assessmentId).result

  override def get(studentId: UniversityID, assessmentId: UUID): DBIO[StoredStudentAssessment] =
    studentAssessments.table.filter(_.studentId === studentId).filter(_.assessmentId === assessmentId).result.head

}
