package domain.dao

import java.time.OffsetDateTime
import java.util.UUID
import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, ExtendedPostgresProfile, PostgresCustomJdbcTypes, StudentAssessment}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
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
}

@Singleton
class AssessmentClientNetworkActivityDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes,
  tables: AssessmentTables,
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityDao with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._
  import tables._

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

//  Distinct on doesn't work correctly on slick https://github.com/slick/slick/issues/1340
//  Followed https://gist.github.com/missingfaktor/aa6c264c5b7411fa48a6a5b654dd0917
  override def getLatestActivityFor(studentAssessmentIds: Seq[UUID]): profile.api.DBIO[Seq[AssessmentClientNetworkActivity]] = {
    assessmentClientNetworkActivities
      .filter(assessmentFilter(studentAssessmentIds, _))
      .join {
        assessmentClientNetworkActivities
          .filter(assessmentFilter(studentAssessmentIds, _))
          .groupBy(e => (e.studentAssessmentId))
          .map { case (key, values) => (key, values.map(_.timestamp).max)}
      }
      .on { case (record, (key, timestamp)) => record.studentAssessmentId === key && record.timestamp === timestamp}
      .map { case (record, _) => record }
      .result
  }

  private def assessmentFilter(studentAssessmentIds: Seq[UUID], e: AssessmentClientNetworkActivities) =
    if (studentAssessmentIds.nonEmpty) { e.studentAssessmentId.inSet(studentAssessmentIds).getOrElse(false) } else { LiteralColumn(true) }

  private def clientActivityForQuery(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]) = {
    assessmentClientNetworkActivities
      .filter{ e =>
        startDateOpt.map { startDate => e.timestamp >= startDate}.getOrElse(LiteralColumn(true)) &&
        endDateOpt.map { endDate => e.timestamp <= endDate}.getOrElse(LiteralColumn(true)) &&
        assessmentFilter(assessments.map(_.id), e)
      }
  }
}
