package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.dao.UploadedFilesTables.StoredUploadedFile
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

    def asStudentAssessmentMetadata: StudentAssessmentMetadata =
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
  def loadAllWithUploadedFiles: DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]]
  def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]]
  def loadByAssessmentIdWithUploadedFiles(assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]]
  def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]]
  def loadByUniversityIdWithUploadedFiles(studentId: UniversityID): DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]]
  def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]]
  def loadWithUploadedFiles(studentId: UniversityID, assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]]
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

  private def allQuery: Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table

  override def all: DBIO[Seq[StoredStudentAssessment]] =
    allQuery.result

  override def loadAllWithUploadedFiles: DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]] =
    allQuery.withUploadedFiles.result

  override def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.insert(assessment)

  override def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.update(studentAssessment)

  private def getByUniversityIdQuery(studentId: UniversityID): Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table.filter(_.studentId === studentId)

  override def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]] =
    getByUniversityIdQuery(studentId).result

  override def loadByUniversityIdWithUploadedFiles(studentId: UniversityID): DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]] =
    getByUniversityIdQuery(studentId).withUploadedFiles.result

  private def getByAssessmentIdQuery(assessmentId: UUID): Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table.filter(_.assessmentId === assessmentId)

  override def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]] =
    getByAssessmentIdQuery(assessmentId).result

  override def loadByAssessmentIdWithUploadedFiles(assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]] =
    getByAssessmentIdQuery(assessmentId).withUploadedFiles.result

  private def getQuery(studentId: UniversityID, assessmentId: UUID): Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table
      .filter(_.studentId === studentId)
      .filter(_.assessmentId === assessmentId)

  override def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]] =
    getQuery(studentId, assessmentId).result.headOption

  override def loadWithUploadedFiles(studentId: UniversityID, assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Option[StoredUploadedFile])]] =
    getQuery(studentId, assessmentId).withUploadedFiles.result
}
