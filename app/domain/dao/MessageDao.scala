package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import domain._
import domain.dao.AssessmentsTables.StoredAssessment
import domain.messaging.{Message, MessageSave, MessageSender, MessageVersion}
import domain.messaging.{Message, MessageSender, MessageVersion}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import slick.lifted.ProvenShape
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.ExecutionContext

@Singleton
class MessageDao @Inject()(
  @NamedDatabase("default") protected val dbConfigProvider: DatabaseConfigProvider,
  assessmentTables: AssessmentTables,
  val jdbcTypes: PostgresCustomJdbcTypes
)(
  implicit ec: ExecutionContext
) extends HasDatabaseConfigProvider[ExtendedPostgresProfile]
  with MessageTables {

  import profile.api._
  import jdbcTypes._

  def insert(message: Message)(implicit ctx: AuditLogContext): DBIO[Message] =
    messages.insert(message)

  def findById(id: UUID)(implicit ctx: TimingContext): DBIO[Option[Message]] =
    messages.table.filter(_.id === id).result.headOption

  def forAssessment(assessmentId: UUID): DBIO[Seq[Message]] = messages.table
    .filter(_.assessmentId === assessmentId)
    .sortBy(_.created)
    .result

  def forStudentAssessment(assessmentId: UUID, student: UniversityID): DBIO[Seq[Message]] = messages.table
    .filter(_.assessmentId === assessmentId)
    .filter(_.studentId === student)
    .sortBy(_.created)
    .result

  def findByDepartmentCode(departmentCode: DepartmentCode): DBIO[Seq[(StoredAssessment, Message)]] =
    assessmentTables.assessments.table.filter(_.departmentCode === departmentCode)
      .join(messages.table)
      .on((assessment, message) => message.assessmentId === assessment.id)
      .result
}

trait MessageTables extends VersionedTables {
  this: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  val jdbcTypes: PostgresCustomJdbcTypes

  import profile.api._
  import jdbcTypes._

  sealed trait CommonProperties { self: Table[_] =>
    def text = column[String]("text")
    def sender = column[MessageSender]("sender")
    def studentId = column[UniversityID]("university_id")
    def staffId = column[Option[Usercode]]("staff_id")
    def assessmentId = column[UUID]("assessment_id")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class Messages(tag: Tag) extends Table[Message](tag, "message") with VersionedTable[Message] with CommonProperties {
    override def matchesPrimaryKey(other: Message): Rep[Boolean] = id === other.id

    def id = column[UUID]("id", O.PrimaryKey)

    def * : ProvenShape[Message] = (id, text, sender, studentId, assessmentId, staffId, created, version).mapTo[Message]
  }

  class MessageVersions(tag: Tag) extends Table[MessageVersion](tag, "message_version") with StoredVersionTable[Message] with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    def * = (id, text, sender, studentId, assessmentId, staffId, created, version, operation, timestamp, auditUser).mapTo[MessageVersion]
    def pk = primaryKey("pk_messageversions", (id, timestamp))
    def idx = index("idx_messageversions", (id, version))
  }

  val messages: VersionedTableQuery[Message, MessageVersion, Messages, MessageVersions] =
    VersionedTableQuery(TableQuery[Messages], TableQuery[MessageVersions])


}
