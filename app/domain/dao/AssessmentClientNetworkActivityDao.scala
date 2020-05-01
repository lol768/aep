package domain.dao

import java.sql.JDBCType
import java.time.OffsetDateTime
import java.util.{Calendar, TimeZone, UUID}

import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, ExtendedPostgresProfile, PostgresCustomJdbcTypes, StudentAssessment}
import helpers.LenientTimezoneNameParsing._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.{GetResult, PositionedParameters, SetParameter}
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AssessmentClientNetworkActivityDaoImpl])
trait AssessmentClientNetworkActivityDao {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def insert(activity: AssessmentClientNetworkActivity): DBIO[AssessmentClientNetworkActivity]
  def deleteAll(studentAssessmentId: UUID): DBIO[Int]
  def deleteAll(studentAssessmentIds: Seq[UUID]): DBIO[Int]
  def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]]
  def getClientActivities(offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]]
  def getClientActivityFor(assessments: Seq[StudentAssessment], startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime], offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]]
  def countClientActivityFor(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]): DBIO[Int]
  def getLatestActivityFor(studentAssessmentIds: Seq[UUID]): DBIO[Seq[AssessmentClientNetworkActivity]]
  def getLatestInvigilatorActivityFor(assessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]]
}

@Singleton
class AssessmentClientNetworkActivityDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
  tables: AssessmentTables,
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import tables._
  import jdbcTypes._

  override def insert(activity: AssessmentClientNetworkActivity): DBIO[AssessmentClientNetworkActivity] =
    (assessmentClientNetworkActivities += activity).map(_ => activity)

  override def deleteAll(studentAssessmentId: UUID): DBIO[Int] =
    assessmentClientNetworkActivities.filter { a => a.studentAssessmentId === studentAssessmentId }.delete

  override def deleteAll(studentAssessmentIds: Seq[UUID]): DBIO[Int] =
    assessmentClientNetworkActivities.filter(_.studentAssessmentId inSetBind studentAssessmentIds).delete

  override def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]] =
    assessmentClientNetworkActivities.filter { a => a.studentAssessmentId === studentAssessmentId }.result
  override def getClientActivities(offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]] = {
    assessmentClientNetworkActivities
      .sortBy(_.timestamp.asc)
      .drop(offset)
      .take(numberToReturn)
      .result
  }

  override def getClientActivityFor(assessments: Seq[StudentAssessment], startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime], offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]] = {
    clientActivityForQuery(assessments, startDateOpt, endDateOpt)
      .sortBy(_.timestamp.asc)
      .drop(offset)
      .take(numberToReturn)
      .result
  }

  override def countClientActivityFor(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]): DBIO[Int] = {
    clientActivityForQuery(assessments, startDateOpt, endDateOpt).length.result
  }

  private[this] val timestampUTCReferenceCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

  private[this] implicit val getAssessmentClientNetworkActivityResult: GetResult[AssessmentClientNetworkActivity] =
    GetResult(r => AssessmentClientNetworkActivity(
      downlink = r.nextDoubleOption,
      downlinkMax = r.nextDoubleOption,
      effectiveType = r.nextStringOption,
      rtt = r.nextIntOption,
      `type` = r.nextStringOption,
      studentAssessmentId = r.nextStringOption.map(UUID.fromString),
      assessmentId = r.nextStringOption.map(UUID.fromString),
      usercode = r.nextStringOption.map(Usercode.apply),
      localTimezoneName = r.nextStringOption.map(_.maybeZoneId),
      timestamp = {
        // We know that it's UTC in the database. r.nextTimestamp() doesn't pass a reference calendar, so we'll do it ourselves
        val resultSet = r.rs

        // Tell the PositionedResult to skip over the next column, we're dealing with it ourselves
        r.skip

        val ts = resultSet.getTimestamp(r.currentPos, timestampUTCReferenceCalendar)
        OffsetDateTime.ofInstant(ts.toInstant, JavaTime.timeZone)
      },
    ))

  private[this] val assessmentClientNetworkActivityColumns =
    "downlink, downlink_max, effective_type, rtt, type, student_assessment_id, assessment_id, usercode, local_timezone_name, timestamp_utc"

  override def getLatestActivityFor(studentAssessmentIds: Seq[UUID]): DBIO[Seq[AssessmentClientNetworkActivity]] = {
    if (studentAssessmentIds.isEmpty) return DBIO.successful(Seq.empty)

    //  Distinct on doesn't work correctly on slick https://github.com/slick/slick/issues/1340
    // If this is ever fixed, this simple bit of Slick can replace the mess below
    //    assessmentClientNetworkActivities
    //      .distinctOn(_.studentAssessmentId)
    //      .filter(assessmentFilter(studentAssessmentIds, _))
    //      .sortBy(a => (a.studentAssessmentId, a.timestamp.desc))
    //      .result

    implicit val setUUIDListParameter: SetParameter[Seq[UUID]] =
      (v1: Seq[UUID], v2: PositionedParameters) => v1.foreach(v2.setObject(_, JDBCType.BINARY.getVendorTypeNumber))

    sql"""select distinct on (student_assessment_id)
           #$assessmentClientNetworkActivityColumns
         from assessment_client_network_activity
         where student_assessment_id in ($studentAssessmentIds#${",?" * (studentAssessmentIds.size - 1)})
         order by student_assessment_id, timestamp_utc desc""".as[AssessmentClientNetworkActivity]
  }

  override def getLatestInvigilatorActivityFor(assessmentId: UUID): profile.api.DBIO[Seq[AssessmentClientNetworkActivity]] = {
    //  Distinct on doesn't work correctly on slick https://github.com/slick/slick/issues/1340
    // If this is ever fixed, this simple bit of Slick can replace the mess below
    //    assessmentClientNetworkActivities
    //      .distinctOn(_.usercode)
    //      .filter { a => a.assessmentId === assessmentId && a.studentAssessmentId.isEmpty }
    //      .sortBy(a => (a.usercode, a.timestamp.desc))
    //      .result

    implicit val setUUIDParameter: SetParameter[UUID] =
      (v1: UUID, v2: PositionedParameters) => v2.setObject(v1, JDBCType.BINARY.getVendorTypeNumber)

    sql"""select distinct on (usercode)
           #$assessmentClientNetworkActivityColumns
         from assessment_client_network_activity
         where assessment_id = $assessmentId and student_assessment_id is null
         order by usercode, timestamp_utc desc""".as[AssessmentClientNetworkActivity]
  }

  private def assessmentFilter(studentAssessmentIds: Seq[UUID], e: AssessmentClientNetworkActivities) =
    e.studentAssessmentId inSetBind studentAssessmentIds

  private def clientActivityForQuery(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]) = {
    assessmentClientNetworkActivities
      .filter { e =>
        startDateOpt.map { startDate => e.timestamp >= startDate}.getOrElse(LiteralColumn(true)) &&
        endDateOpt.map { endDate => e.timestamp <= endDate}.getOrElse(LiteralColumn(true)) &&
        assessmentFilter(assessments.map(_.id), e)
      }
  }
}
