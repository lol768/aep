package domain.dao

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
  def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]]
  def getClientActivities(offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]]
  def countClientActivities(): DBIO[Int]
  def getClientActivityFor(assessments: Seq[StudentAssessment], startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime], offset: Int, numberToReturn: Int): DBIO[Seq[AssessmentClientNetworkActivity]]
  def countClientActivityFor(assessments: Seq[StudentAssessment],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]): DBIO[Int]
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

  override def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]] =
    assessmentClientNetworkActivities.filter { a => a.studentAssessmentId === studentAssessmentId }.result
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
