package domain.dao

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{AssessmentClientNetworkActivity, ExtendedPostgresProfile, PostgresCustomJdbcTypes}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AssessmentClientNetworkActivityDaoImpl])
trait AssessmentClientNetworkActivityDao {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def insert(activity: AssessmentClientNetworkActivity): DBIO[AssessmentClientNetworkActivity]
  def findByStudentAssessmentId(studentAssessmentId: UUID): DBIO[Seq[AssessmentClientNetworkActivity]]
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
}
