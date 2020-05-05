package domain.dao

import java.time.{Duration, LocalDate, OffsetDateTime}
import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.Assessment.Platform.OnlineExams
import domain.Assessment._
import domain._
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.{StoredDeclarations, StoredStudentAssessment}
import domain.dao.UploadedFilesTables.StoredUploadedFile
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json._
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
    durationStyle: DurationStyle,
    storedBrief: StoredBrief,
    invigilators: List[String],
    state: State,
    tabulaAssessmentId: Option[UUID],
    tabulaAssignments: List[UUID],
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
        durationStyle,
        storedBrief.asBrief(fileMap, platform),
        invigilators.map(Usercode).toSet,
        state,
        tabulaAssessmentId,
        tabulaAssignments.toSet,
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
        durationStyle,
        storedBrief.asBriefWithoutFiles(platform),
        invigilators.map(Usercode).toSet,
        state,
        tabulaAssessmentId,
        tabulaAssignments.toSet,
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
        durationStyle,
        storedBrief,
        invigilators,
        state,
        tabulaAssessmentId,
        tabulaAssignments,
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
    durationStyle: DurationStyle,
    storedBrief: StoredBrief,
    invigilators: List[String],
    state: State,
    tabulaAssessmentId: Option[UUID],
    tabulaAssignments: List[UUID],
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
    url: Option[String], // No longer in use; retained for existing data
    urls: Option[Map[Platform, String]], // Not really optional, but used to handle legacy data
  ) {
    def asBrief(fileMap: Map[UUID, UploadedFile], platforms: Set[Platform]): Brief =
      asBriefWithoutFiles(platforms).copy(
        files = fileIds.map(fileMap)
      )

    def asBriefWithoutFiles(platforms: Set[Platform]): Brief =
      Brief(
        text,
        fileIds.map(_ => null),
        urls.getOrElse(
          // If the Map is missing (rather than empty) then handle the legacy url field
          // No real way to know which platform that required a URL this one if for,
          // so set it for all the selected platforms
          // If there are platforms that require a URL but a legacy URL doesn't exist, add empty string
          // This should be caught be validation when saving an assessment
          platforms.filter(_.requiresUrl).map(p => p -> url.getOrElse("")).toMap
        )
      )
  }

  object StoredBrief {
    implicit private val platformMapFormat: Format[Map[Platform, String]] = Format(
      Reads.map[String].map(_.map { case (k, v) => Platform.namesToValuesMap(k) -> v }),
      (o: Map[Platform, String]) => JsObject(o.map { case (k, v) => k.entryName -> JsString(v) }.toSeq)
    )
    implicit val format: Format[StoredBrief] = Json.format[StoredBrief]

    def empty: StoredBrief = StoredBrief(None, Seq.empty, None, Some(Map.empty))

    def apply(text: Option[String], fileIds: Seq[UUID], urls: Map[Platform, String]): StoredBrief = StoredBrief(text, fileIds, None, Some(urls))
  }

}


@ImplementedBy(classOf[AssessmentDaoImpl])
trait AssessmentDao {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def all: DBIO[Seq[StoredAssessment]]

  def loadAllWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def loadAllWithStudentCount: DBIO[Seq[(StoredAssessment, Int)]]

  def findByStates(states: Seq[State]): DBIO[Seq[StoredAssessment]]

  def findByStatesWithUploadedFiles(states: Seq[State]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def insert(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment]

  def update(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[StoredAssessment]

  def delete(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[Done]

  def getById(id: UUID): DBIO[Option[StoredAssessment]]

  def loadByTabulaAssessmentIdWithUploadedFiles(id: UUID, examProfileCode: String): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]]

  def loadByIdWithUploadedFiles(id: UUID): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]]

  def getByIds(ids: Seq[UUID]): DBIO[Seq[StoredAssessment]]

  def loadByIdsWithUploadedFiles(ids: Seq[UUID]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def getByPaper(paperCode: String, section: Option[String], examProfileCode: String): DBIO[Option[StoredAssessment]]

  def getToday: DBIO[Seq[StoredAssessment]]
  def getTodayWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def getLast48Hrs: DBIO[Seq[StoredAssessment]]
  def getLast48HrsWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]
  def getAssessmentsStartingOnWithStudentCount(date: LocalDate): DBIO[Seq[(StoredAssessment, Int)]]

  def getAssessmentsRequiringUpload: DBIO[Seq[StoredAssessment]]

  def isInvigilator(usercode: Usercode): DBIO[Boolean]

  def getByInvigilatorWithUploadedFiles(usercodes: Set[Usercode]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]

  def getByInvigilatorWithStudentCount(usercodes: Set[Usercode]): DBIO[Seq[(StoredAssessment, Int)]]

  def getByIdAndInvigilatorWithUploadedFiles(id: UUID, usercodes: List[Usercode]): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]]

  def getByExamProfileCodeWithUploadedFiles(examProfileCode: String): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]]
  def getByExamProfileCodeWithStudentCount(examProfileCode: String): DBIO[Seq[(StoredAssessment, Int)]]

  def getFinishedAssessmentsWithSittingInformation(department: Option[DepartmentCode], importedOnly: Boolean): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile], Set[(StoredStudentAssessment, Option[StoredDeclarations], Set[StoredUploadedFile])])]]
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

  override def loadAllWithStudentCount: DBIO[Seq[(StoredAssessment, Int)]] =
    allQuery.withStudentCount
      .sortBy { case (a, _) => (a.startTime, a.paperCode, a.section) }
      .result

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

  override def delete(assessment: StoredAssessment)(implicit ac: AuditLogContext): DBIO[Done] =
    assessments.delete(assessment)

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

  private def getTodayQuery: Query[Assessments, StoredAssessment, Seq] = {
    val today = JavaTime.localDate.atStartOfDay(JavaTime.timeZone).toOffsetDateTime
    assessments.table
      .filter(a => a.startTime >= today && a.startTime < today.plusDays(1))
  }

  private def getLast48HrsQuery: Query[Assessments, StoredAssessment, Seq] = {
    val startTime = JavaTime.offsetDateTime.minusDays(2L)
    val endTime = JavaTime.localDate.atStartOfDay(JavaTime.timeZone).toOffsetDateTime.plusDays(1L)
    assessments.table.filter(a => a.startTime >= startTime && a.startTime < endTime)
  }

  override def getToday: DBIO[Seq[StoredAssessment]] =
    getTodayQuery.result

  override def getTodayWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    getTodayQuery.withUploadedFiles.result
      .map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  override def getLast48Hrs: DBIO[Seq[StoredAssessment]] =
    getLast48HrsQuery.result

  override def getLast48HrsWithUploadedFiles: DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    getLast48HrsQuery.withUploadedFiles.result
      .map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  override def getAssessmentsStartingOnWithStudentCount(date: LocalDate): profile.api.DBIO[Seq[(StoredAssessment, Int)]] = {
    val startOfDay = date.atStartOfDay(JavaTime.timeZone).toOffsetDateTime
    val endOfDay = date.plusDays(1).atStartOfDay(JavaTime.timeZone).toOffsetDateTime

    assessments.table
      .filter(a => a.startTime >= startOfDay && a.startTime < endOfDay)
      .withStudentCount
      .sortBy { case (a, _) => (a.startTime, a.paperCode, a.section) }
      .result
  }

  // finds assessments where there in no possibility of further submissions being made
  private def pastLastSubmitTimeQuery: Query[Assessments, StoredAssessment, Seq] = {
    assessments.table.filter { a =>
      // "epoch" is the interval in seconds
      def durationInHours: Rep[Double] =
        (a.duration.part("epoch") / 3600.0).getOrElse(0.0)

      // Make sure we've waited until the student with the longest extension
      def biggestAdjustmentPerHour: Rep[Duration] = studentAssessments.table
        .filter(_.assessmentId === a.id)
        .map(_.extraTimeAdjustmentPerHour)
        .max
        .getOrElse(Duration.ZERO)

      def biggestAdjustment: Rep[Duration] =
        biggestAdjustmentPerHour * durationInHours

      val totalTime = Case If a.isDayWindow Then {
        LiteralColumn(Option(Assessment.dayWindow))
      } Else {
        a.duration +
          Assessment.uploadGraceDuration +
          Assessment.lateSubmissionPeriod +
          biggestAdjustment
      }

      val lastTime: Rep[Option[OffsetDateTime]] =
        a.startTime +++ (totalTime + Assessment.uploadProcessDuration)

      lastTime < JavaTime.offsetDateTime
    }
  }

  override def getAssessmentsRequiringUpload: DBIO[Seq[StoredAssessment]] = {

    def unsubmittedStudents(id: Rep[UUID]): Rep[Boolean] = studentAssessments.table.filter(_.assessmentId === id)
      .filter(sa => sa.tabulaSubmissionId.isEmpty && sa.uploadedFiles.length() > 0.bind)
      .exists

    pastLastSubmitTimeQuery
      // platform contains OnlineExams - had to come up with this nonsense as the column is a varchar
      .filter(_.platform.asColumnOf[String] like s"%${OnlineExams.entryName}%")
      .filter(a => unsubmittedStudents(a.id))
      .filter(a => !(DepartmentCode("IB").bind === a.departmentCode)) // INC0869937
      .sortBy(a => (a.startTime, a.duration))
      .result
  }

  override def isInvigilator(usercode: Usercode): DBIO[Boolean] =
    assessments.table.filter(_.invigilators @> List(usercode.string)).exists.result

  private def getByInvigilatorQuery(usercodes: Set[Usercode]): Query[Assessments, StoredAssessment, Seq] =
    assessments.table
      .filter(_.invigilators @> usercodes.toList.map(_.string))

  override def getByInvigilatorWithUploadedFiles(usercodes: Set[Usercode]): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    getByInvigilatorQuery(usercodes)
      .withUploadedFiles
      .result
      .map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  override def getByInvigilatorWithStudentCount(usercodes: Set[Usercode]): DBIO[Seq[(StoredAssessment, Int)]] =
    getByInvigilatorQuery(usercodes)
      .withStudentCount
      .sortBy { case (a, _) => (a.startTime, a.paperCode, a.section) }
      .result

  override def getByIdAndInvigilatorWithUploadedFiles(id: UUID, usercodes: List[Usercode]): DBIO[Option[(StoredAssessment, Set[StoredUploadedFile])]] =
    assessments.table
      .filter(_.id === id)
      .filter(_.invigilators @> usercodes.map(_.string))
      .withUploadedFiles
      .result
      .map(OneToMany.leftJoinUnordered(_).headOption)

  override def getByExamProfileCodeWithUploadedFiles(examProfileCode: String): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile])]] =
    assessments.table
      .filter(_.examProfileCode === examProfileCode)
      .withUploadedFiles
      .result
      .map(OneToMany.leftJoinUnordered(_).sortBy(_._1))

  override def getByExamProfileCodeWithStudentCount(examProfileCode: String): DBIO[Seq[(StoredAssessment, Int)]] =
    assessments.table
      .filter(_.examProfileCode === examProfileCode)
      .joinLeft(tables.studentAssessments.table)
      .on(_.id === _.assessmentId)
      .groupBy { case (a, _) => a }
      .map { case (a, q) => (a, q.map(_._2.map(_.id)).countDefined) }
      .sortBy { case (a, _) => (a.startTime, a.paperCode, a.section) }
      .result

  override def getFinishedAssessmentsWithSittingInformation(department: Option[DepartmentCode], importedOnly: Boolean): DBIO[Seq[(StoredAssessment, Set[StoredUploadedFile], Set[(StoredStudentAssessment, Option[StoredDeclarations], Set[StoredUploadedFile])])]] =
    pastLastSubmitTimeQuery
      .filter { a =>
        (if (importedOnly) a.tabulaAssessmentId.nonEmpty else true.bind) &&
        (if (department.nonEmpty) a.departmentCode === department.get else true.bind)
      }
      .withUploadedFiles
      .joinLeft(
        tables.studentAssessments.table
          .withDeclarationAndUploadedFiles
      )
      .on { case ((a, _), (sa, _, _)) => a.id === sa.assessmentId }
      .result
      .map { rows =>
        OneToMany.leftJoinUnordered(rows.map(_._1).distinct).sortBy(_._1).map { case (assessment, assessmentFiles) =>
          (
            assessment,
            assessmentFiles,
            rows.groupBy(_._1._1) // Rows grouped by assessment
              .getOrElse(assessment, Seq.empty)
              .map(_._2).distinct.flatten // Student assessment rows
              .groupBy(_._1) // Grouped by student assessment
              .map { case (studentAssessment, saRows) =>
                (studentAssessment, saRows.head._2, saRows.flatMap(_._3).toSet)
              }
              .toSet
          )
        }
      }

}
