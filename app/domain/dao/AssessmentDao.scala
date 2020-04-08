package domain.dao

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment._
import domain._
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.UploadedFilesTables.StoredUploadedFile
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{Format, Json}
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

object AssessmentsTables {

  case class StoredAssessment(
    id: UUID = UUID.randomUUID(),
    paperCode: String,
    section: Option[String],
    title: String,
    startTime: Option[OffsetDateTime],
    duration: Option[Duration],
    platform: Set[Platform],
    assessmentType: Option[AssessmentType],
    storedBrief: StoredBrief,
    invigilators: List[String],
    state: State,
    tabulaAssessmentId: Option[UUID],
    examProfileCode: String,
    moduleCode: String,
    departmentCode: DepartmentCode,
    sequence: String, //MAB sequence
    created: OffsetDateTime,
    version: OffsetDateTime,
  ) extends Versioned[StoredAssessment] with DefinesStartWindow {

    def asAssessment(fileMap: Map[UUID, UploadedFile]): Assessment =
      Assessment(
        id,
        paperCode,
        section,
        title,
        startTime,
        duration,
        platform,
        assessmentType,
        storedBrief.asBrief(fileMap),
        invigilators.map(Usercode).toSet,
        state,
        tabulaAssessmentId,
        examProfileCode,
        moduleCode,
        departmentCode,
        sequence
      )

    def asAssessmentMetadata: AssessmentMetadata =
      AssessmentMetadata(
        id,
        paperCode,
        section,
        title,
        startTime,
        duration,
        platform,
        assessmentType,
        state,
        tabulaAssessmentId,
        examProfileCode,
        moduleCode,
        departmentCode,
        sequence
      )

    override def atVersion(at: OffsetDateTime): StoredAssessment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredAssessment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredAssessmentVersion(
        id,
        paperCode,
        section,
        title,
        startTime,
        duration,
        platform,
        assessmentType,
        storedBrief,
        invigilators,
        state,
        tabulaAssessmentId,
        examProfileCode,
        moduleCode,
        departmentCode,
        sequence,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  object StoredAssessment {
    def tupled = (apply _).tupled

    implicit val dateOrdering: Ordering[StoredAssessment] = Ordering.by { a => (a.startTime.map(_.toEpochSecond).getOrElse(Long.MaxValue), a.paperCode, a.section) }
  }

  case class StoredAssessmentVersion(
    id: UUID = UUID.randomUUID(),
    paperCode: String,
    section: Option[String],
    title: String,
    startTime: Option[OffsetDateTime],
    duration: Option[Duration],
    platform: Set[Platform],
    assessmentType: Option[AssessmentType],
    storedBrief: StoredBrief,
    invigilators: List[String],
    state: State,
    tabulaAssessmentId: Option[UUID],
    examProfileCode: String,
    moduleCode: String,
    departmentCode: DepartmentCode,
    sequence: String,
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
    def asBrief(fileMap: Map[UUID, UploadedFile]): Brief =
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
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredAssessment]]

  def loadAllWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def findByStates(states: Seq[State]): DBIO[Seq[StoredAssessment]]

  def findByStatesWithUploadedFiles(states: Seq[State]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def insert(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment]

  def update(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment]

  def getById(id: UUID): DBIO[Option[StoredAssessment]]

  def loadByTabulaAssessmentIdWithUploadedFiles(id: UUID, examProfileCode: String): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]]

  def loadByIdWithUploadedFiles(id: UUID): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]]

  def getByIds(ids: Seq[UUID]): DBIO[Seq[StoredAssessment]]

  def loadByIdsWithUploadedFiles(ids: Seq[UUID]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def getByPaper(paperCode: String, section: Option[String], examProfileCode: String): DBIO[Option[StoredAssessment]]

  def getToday: DBIO[Seq[StoredAssessment]]

  def isInvigilator(usercode: Usercode): DBIO[Boolean]

  def getByInvigilator(usercodes: Set[Usercode]): DBIO[Seq[StoredAssessment]]

  def getByIdAndInvigilator(id: UUID, usercodes: List[Usercode]): DBIO[Option[StoredAssessment]]
}

@Singleton
class AssessmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
  tables: AssessmentTables,
)(implicit ec: ExecutionContext) extends AssessmentDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  import profile.api._
  import jdbcTypes._
  import tables._

  private def allQuery: Query[Assessments, StoredAssessment, Seq] =
    assessments.table

  override def all: DBIO[Seq[StoredAssessment]] =
    allQuery.result

  override def loadAllWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    allQuery.withUploadedFiles.result.map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  private def findByStatesQuery(states: Seq[State]): Query[Assessments, StoredAssessment, Seq] =
    assessments.table.filter(_.state inSetBind states)

  override def findByStates(states: Seq[State]): DBIO[Seq[StoredAssessment]] =
    findByStatesQuery(states).result

  override def findByStatesWithUploadedFiles(states: Seq[State]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    findByStatesQuery(states)
      .withUploadedFiles.result
      .map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  override def insert(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment] =
    assessments.insert(assessment)

  override def update(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment] =
    assessments.update(assessment)

  private def getByIdQuery(id: UUID): Query[Assessments, StoredAssessment, Seq] =
    assessments.table.filter(_.id === id)

  override def getById(id: UUID): DBIO[Option[StoredAssessment]] =
    getByIdQuery(id).result.headOption

  override def loadByTabulaAssessmentIdWithUploadedFiles(id: UUID, examProfileCode: String): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]] =
    assessments.table
      .filter(_.tabulaAssessmentId === id)
      .filter(_.examProfileCode === examProfileCode)
      .withUploadedFiles.result
      .map(OneToMany.leftJoinUnordered(_).headOption)

  override def loadByIdWithUploadedFiles(id: UUID): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]] =
    getByIdQuery(id).withUploadedFiles.result
      .map(OneToMany.leftJoinUnordered(_).headOption)

  private def getByIdsQuery(ids: Seq[UUID]): Query[Assessments, StoredAssessment, Seq] =
    assessments.table.filter(_.id inSetBind ids)

  override def getByIds(ids: Seq[UUID]): DBIO[Seq[StoredAssessment]] =
    getByIdsQuery(ids).result

  override def loadByIdsWithUploadedFiles(ids: Seq[UUID]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    getByIdsQuery(ids).withUploadedFiles.result.map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  override def getByPaper(paperCode: String, section: Option[String], examProfileCode: String): DBIO[Option[StoredAssessment]] =
    assessments.table
      .filter(_.paperCode === paperCode)
      .filter { a =>
        if (section.isEmpty) a.section.isEmpty
        else a.section.nonEmpty && a.section.get === section.get
      }
      .filter(_.examProfileCode === examProfileCode)
      .result.headOption

  override def getToday: DBIO[Seq[StoredAssessment]] = {
    val today = JavaTime.localDate.atStartOfDay(JavaTime.timeZone).toOffsetDateTime
    assessments.table.filter(a => a.startTime >= today && a.startTime < today.plusDays(1)).result
  }

  override def isInvigilator(usercode: Usercode): DBIO[Boolean] =
    assessments.table.filter(_.invigilators @> List(usercode.string)).exists.result

  override def getByInvigilator(usercodes: Set[Usercode]): DBIO[Seq[StoredAssessment]] =
    assessments.table.filter(_.invigilators @> usercodes.toList.map(_.string)).result

  override def getByIdAndInvigilator(id: UUID, usercodes: List[Usercode]): DBIO[Option[StoredAssessment]] =
    assessments.table.filter(_.id === id).filter(_.invigilators @> usercodes.map(_.string)).result.headOption
}
