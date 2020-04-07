package domain.dao

import java.time.{Duration, OffsetDateTime}
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
    extraTimeAdjustment: Option[Duration],
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
        extraTimeAdjustment,
        finaliseTime,
        uploadedFiles.map(fileMap)
      )

    def asStudentAssessmentMetadata: StudentAssessmentMetadata =
      StudentAssessmentMetadata(
        assessmentId,
        id,
        studentId,
        inSeat,
        startTime,
        extraTimeAdjustment,
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
        extraTimeAdjustment,
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
    extraTimeAdjustment: Option[Duration],
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
  def loadAllWithUploadedFiles: DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]]
  def getByAssessmentIds(assessmentIds: Seq[UUID]): DBIO[Seq[StoredStudentAssessment]]
  def loadByAssessmentIdWithUploadedFiles(assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]]
  def loadByUniversityIdWithUploadedFiles(studentId: UniversityID): DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]]
  def loadWithUploadedFiles(studentId: UniversityID, assessmentId: UUID): DBIO[Option[(StoredStudentAssessment, Set[StoredUploadedFile])]]

  def delete(studentId: UniversityID, assessmentId: UUID): DBIO[Int]
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

  override def loadAllWithUploadedFiles: DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]] =
    allQuery.withUploadedFiles.result.map(OneToMany.leftJoinUnordered)

  override def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.insert(assessment)

  override def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment] =
    studentAssessments.update(studentAssessment)

  private def getByUniversityIdQuery(studentId: UniversityID): Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table.filter(_.studentId === studentId)

  override def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]] =
    getByUniversityIdQuery(studentId).result

  override def loadByUniversityIdWithUploadedFiles(studentId: UniversityID): DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]] =
    getByUniversityIdQuery(studentId).withUploadedFiles.result.map(OneToMany.leftJoinUnordered)

  private def getByAssessmentIdQuery(assessmentId: UUID): Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table.filter(_.assessmentId === assessmentId)

  override def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]] =
    getByAssessmentIdQuery(assessmentId).result

  override def getByAssessmentIds(assessmentIds: Seq[UUID]): profile.api.DBIO[Seq[StoredStudentAssessment]] =
    studentAssessments.table.filter(_.assessmentId inSetBind assessmentIds).result

  override def loadByAssessmentIdWithUploadedFiles(assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]] =
    getByAssessmentIdQuery(assessmentId).withUploadedFiles.result.map(OneToMany.leftJoinUnordered)

  private def getQuery(studentId: UniversityID, assessmentId: UUID): Query[StudentAssessments, StoredStudentAssessment, Seq] =
    studentAssessments.table
      .filter(_.studentId === studentId)
      .filter(_.assessmentId === assessmentId)

  override def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]] =
    getQuery(studentId, assessmentId).result.headOption

  override def loadWithUploadedFiles(studentId: UniversityID, assessmentId: UUID): DBIO[Option[(StoredStudentAssessment, Set[StoredUploadedFile])]] =
    getQuery(studentId, assessmentId).withUploadedFiles.result
      .map(OneToMany.leftJoinUnordered(_).headOption)

  override def delete(studentId: UniversityID, assessmentId: UUID): DBIO[Int] =
    getQuery(studentId, assessmentId).delete
}
