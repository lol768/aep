package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, ExtendedPostgresProfile, PostgresCustomJdbcTypes}
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
  def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]]
}


@Singleton
class AssessmentClientNetworkActivityDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityDao with AssessmentClientNetworkActivityTable with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._

  override def insert(activity: AssessmentClientNetworkActivity): DBIO[AssessmentClientNetworkActivity] =
    (assessmentClientNetworkActivities += activity).map(_ => activity)

  override def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]] =
    assessmentClientNetworkActivities.filter { a => a.studentAssessmentId === studentAssessmentId }.result
}
