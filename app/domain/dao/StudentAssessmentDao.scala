package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

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
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredStudentAssessment]]
  def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]]
  def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]]
  def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]]
}

@Singleton
class StudentAssessmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
  tables: AssessmentTables,
)(implicit ec: ExecutionContext) extends StudentAssessmentDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import jdbcTypes._
  import tables._

  override def all: DBIO[Seq[StoredStudentAssessment]] = studentAssessments.result

  override def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.insert(assessment)

  override def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.update(studentAssessment)

  override def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]] =
    studentAssessments.table.filter(_.studentId === studentId).result

  override def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]] =
    studentAssessments.table.filter(_.assessmentId === assessmentId).result

  override def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]] =
    studentAssessments.table.filter(_.studentId === studentId).filter(_.assessmentId === assessmentId).result.headOption

}
