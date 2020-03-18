package domain.dao

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment.{AssessmentType, Brief}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.ExecutionContext

trait AssessmentsTable {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: CustomJdbcTypes

  import jdbcTypes._

  /**
    * Table classes defined inside a DAO (or other instance with access to the Slick profile)
    * because column definitions depend on profile.api._
    */
  class Assessments(tag: Tag) extends Table[Assessment](tag, "assessment") {
    def id = column[UUID]("id", O.PrimaryKey)
    def code = column[String]("code")
    def startTime = column[OffsetDateTime]("start_time")
    def duration = column[Duration]("duration")
    def assessmentType = column[AssessmentType]("assessment_type")
    def brief = column[Brief]("brief")

    def * = (id, code, startTime, duration, assessmentType, brief).mapTo[Assessment]
  }

  val assessments = TableQuery[Assessments]
}


@ImplementedBy(classOf[AssessmentDaoImpl])
trait AssessmentDao {
  self: AssessmentsTable with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def insert(event: Assessment): DBIO[Assessment]
  def getById(id: UUID): DBIO[Option[Assessment]]
}

@Singleton
class AssessmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: CustomJdbcTypes
)(implicit ec: ExecutionContext) extends AssessmentDao with AssessmentsTable with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._

  override def insert(assessment: Assessment): DBIO[Assessment] =
    (assessments += assessment).map(_ => assessment)

  override def getById(id: UUID): DBIO[Option[Assessment]] =
    assessments.filter(_.id === id).result.headOption
}
