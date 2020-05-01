package domain.dao

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{StoredVersion, _}
import domain.dao.StudentAssessmentsTables.{StoredDeclarations, StoredStudentAssessment}
import domain.dao.UploadedFilesTables.StoredUploadedFile
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import uk.ac.warwick.util.termdates.AcademicYear
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

object StudentAssessmentsTables {

  case class StoredStudentAssessment(
    id: UUID,
    assessmentId: UUID,
    occurrence: Option[String],
    academicYear: Option[AcademicYear],
    studentId: UniversityID,
    inSeat: Boolean,
    startTime: Option[OffsetDateTime],
    extraTimeAdjustmentPerHour: Option[Duration],
    finaliseTime: Option[OffsetDateTime],
    uploadedFiles: List[UUID],
    tabulaSubmissionId: Option[UUID],
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredStudentAssessment] with DefinesExtraTimeAdjustment {
    def asStudentAssessment(fileMap: Map[UUID, UploadedFile]): StudentAssessment =
      StudentAssessment(
        id,
        assessmentId,
        occurrence,
        academicYear,
        studentId,
        inSeat,
        startTime,
        extraTimeAdjustmentPerHour,
        finaliseTime,
        uploadedFiles.map(fileMap),
        tabulaSubmissionId
      )

    override def atVersion(at: OffsetDateTime): StoredStudentAssessment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredStudentAssessment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredStudentAssessmentVersion(
        id,
        assessmentId,
        occurrence: Option[String],
        academicYear: Option[AcademicYear],
        studentId,
        inSeat,
        startTime,
        extraTimeAdjustmentPerHour,
        finaliseTime,
        uploadedFiles,
        tabulaSubmissionId,
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
    occurrence: Option[String],
    academicYear: Option[AcademicYear],
    studentId: UniversityID,
    inSeat: Boolean,
    startTime: Option[OffsetDateTime],
    extraTimeAdjustmentPerHour: Option[Duration],
    finaliseTime: Option[OffsetDateTime],
    uploadedFiles: List[UUID],
    tabulaSubmisionId: Option[UUID],
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredStudentAssessment]


  case class StoredDeclarations(
    studentAssessmentId: UUID,
    acceptsAuthorship: Boolean,
    selfDeclaredRA: Boolean,
    completedRA: Boolean,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredDeclarations] {
    def asDeclarations: Declarations =
      Declarations(
        studentAssessmentId,
        acceptsAuthorship,
        selfDeclaredRA,
        completedRA
      )

    override def atVersion(at: OffsetDateTime): StoredDeclarations = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredDeclarations]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredDeclarationsVersion(
        studentAssessmentId,
        acceptsAuthorship,
        selfDeclaredRA,
        completedRA,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredDeclarationsVersion(
    studentAssessmentId: UUID,
    acceptsAuthorship: Boolean,
    selfDeclaredRA: Boolean,
    completedRA: Boolean,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredDeclarations]
}


@ImplementedBy(classOf[StudentAssessmentDaoImpl])
trait StudentAssessmentDao {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredStudentAssessment]]
  def loadAllWithUploadedFiles: DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def insert(assessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def insertAll(assessments: Set[StoredStudentAssessment])(implicit ac: AuditLogContext): DBIO[Seq[StoredStudentAssessment]]
  def update(studentAssessment: StoredStudentAssessment)(implicit ac: AuditLogContext): DBIO[StoredStudentAssessment]
  def getByAssessmentId(assessmentId: UUID): DBIO[Seq[StoredStudentAssessment]]
  def getByAssessmentIds(assessmentIds: Seq[UUID]): DBIO[Seq[StoredStudentAssessment]]
  def loadByAssessmentIdWithUploadedFiles(assessmentId: UUID): DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def getByUniversityId(studentId: UniversityID): DBIO[Seq[StoredStudentAssessment]]
  def loadByUniversityIdWithUploadedFiles(studentId: UniversityID): DBIO[Seq[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def get(studentId: UniversityID, assessmentId: UUID): DBIO[Option[StoredStudentAssessment]]
  def loadWithUploadedFiles(studentId: UniversityID, assessmentId: UUID): DBIO[Option[(StoredStudentAssessment, Set[StoredUploadedFile])]]
  def delete(studentId: UniversityID, assessmentId: UUID)(implicit ac: AuditLogContext): DBIO[Int]

  def insert(declarations: StoredDeclarations)(implicit ac: AuditLogContext): DBIO[StoredDeclarations]
  def update(declarations: StoredDeclarations)(implicit ac: AuditLogContext): DBIO[StoredDeclarations]
  def getDeclarations(declarationsId: UUID): DBIO[Option[StoredDeclarations]]
  def getDeclarations(declarationsIds: Seq[UUID]): DBIO[Seq[StoredDeclarations]]
  def deleteDeclarations(declarationsId: UUID)(implicit ac: AuditLogContext): DBIO[Int]
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

  override def insertAll(assessments: Set[StoredStudentAssessment])(implicit ac: AuditLogContext): DBIO[Seq[StoredStudentAssessment]] =
    studentAssessments.insertAll(assessments.toSeq)

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

  override def delete(studentId: UniversityID, assessmentId: UUID)(implicit ac: AuditLogContext): DBIO[Int] =
    for {
      sa <- getQuery(studentId, assessmentId).result.headOption
      rows <- sa match {
        case Some(studentAssessment) => studentAssessments.delete(studentAssessment).map(_ => 1)
        case _ => DBIO.successful(0)
      }
    } yield rows

  override def insert(decs: StoredDeclarations)(implicit ac: AuditLogContext): DBIO[StoredDeclarations] =
    declarations.insert(decs)

  override def update(decs: StoredDeclarations)(implicit ac: AuditLogContext): DBIO[StoredDeclarations] =
    declarations.update(decs)

  private def getDeclarationsQuery(declarationsId: UUID): Query[Declarations, StoredDeclarations, Seq] =
    declarations.table.filter(_.studentAssessmentId === declarationsId)

  override def getDeclarations(declarationsId: UUID): DBIO[Option[StoredDeclarations]] =
    getDeclarationsQuery(declarationsId).result.headOption

  override def getDeclarations(declarationsIds: Seq[UUID]): DBIO[Seq[StoredDeclarations]] =
    declarations.table.filter(_.studentAssessmentId inSetBind declarationsIds).result

  override def deleteDeclarations(declarationsId: UUID)(implicit ac: AuditLogContext): DBIO[Int] =
    for {
      d <- getDeclarationsQuery(declarationsId).result.headOption
      rows <- d match {
        case Some(declaration) => declarations.delete(declaration).map(_ => 1)
        case _ => DBIO.successful(0)
      }
    } yield rows

}
