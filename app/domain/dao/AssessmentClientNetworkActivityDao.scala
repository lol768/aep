package domain.dao

import java.time.OffsetDateTime
import java.util.UUID
import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, ExtendedPostgresProfile, PostgresCustomJdbcTypes, StudentAssessment}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import scala.concurrent.ExecutionContext

trait AssessmentClientNetworkActivityTable {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: PostgresCustomJdbcTypes

  class AssessmentClientNetworkActivities(tag: Tag) extends Table[AssessmentClientNetworkActivity](tag, "assessment_client_network_activity") {
    def downlink = column[Option[Double]]("downlink")
    def downlinkMax = column[Option[Double]]("downlink_max")
    def effectiveType = column[Option[String]]("effective_type")
    def rtt = column[Option[Int]]("rtt")
    def `type` = column[Option[String]]("type")
    def studentAssessmentId = column[UUID]("student_assessment_id")
    def timestamp = column[OffsetDateTime]("timestamp_utc")

    def * = {
      (downlink, downlinkMax, effectiveType, rtt, `type`, studentAssessmentId, timestamp).mapTo[AssessmentClientNetworkActivity]
    }
  }

  val assessmentClientNetworkActivities = TableQuery[AssessmentClientNetworkActivities]
}


@ImplementedBy(classOf[AssessmentClientNetworkActivityDaoImpl])
trait AssessmentClientNetworkActivityDao {
  self: AssessmentClientNetworkActivityTable with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def insert(activity: AssessmentClientNetworkActivity): DBIO[AssessmentClientNetworkActivity]
  def getClientActivities(offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]]
  def countClientActivities(): DBIO[Int]
  def getClientActivityFor(assessments: Seq[StudentAssessment], startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime], offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]]
  def countClientActivityFor(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]): DBIO[Int]
}


@Singleton
class AssessmentClientNetworkActivityDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityDao with AssessmentClientNetworkActivityTable with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import jdbcTypes._

  override def insert(activity: AssessmentClientNetworkActivity): DBIO[AssessmentClientNetworkActivity] =
    (assessmentClientNetworkActivities += activity).map(_ => activity)

  override def getClientActivities(offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]] = {
    assessmentClientNetworkActivities
      .sortBy(_.timestamp.asc)
      .drop(offset)
      .take(numberToReturn)
      .result
  }

  override def countClientActivities(): DBIO[Int] =
    assessmentClientNetworkActivities
      .length
      .result

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

  private def clientActivityForQuery(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]) = {
    assessmentClientNetworkActivities
      .filter{e =>
        val assessmentFilter = if (assessments.nonEmpty) { e.studentAssessmentId.inSet(assessments.map(_.id)) } else { LiteralColumn(true) }

        startDateOpt.map { startDate => e.timestamp >= startDate}.getOrElse(LiteralColumn(true)) &&
        endDateOpt.map { endDate => e.timestamp <= endDate}.getOrElse(LiteralColumn(true)) &&
        assessmentFilter
      }
  }
}
