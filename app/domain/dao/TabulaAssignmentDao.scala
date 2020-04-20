package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.dao.TabulaAssignmentTables.{StoredTabulaAssignment, StoredTabulaAssignmentVersion}
import domain.tabula.TabulaAssignment
import domain._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.lifted.ProvenShape
import uk.ac.warwick.util.termdates.AcademicYear
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait TabulaAssignmentTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: PostgresCustomJdbcTypes
  import jdbcTypes._

  trait CommonProperties { self: Table[_] =>
    def name = column[String]("name")
    def academicYear = column[AcademicYear]("academic_year")
    def created = column[OffsetDateTime]("created_utc")
    def version = column[OffsetDateTime]("version_utc")
  }

  class TabulaAssignments(tag: Tag) extends Table[StoredTabulaAssignment](tag, "tabula_assignment")
    with VersionedTable[StoredTabulaAssignment]
    with CommonProperties {
    override def matchesPrimaryKey(other: StoredTabulaAssignment): Rep[Boolean] = id === other.id
    def id = column[UUID]("id", O.PrimaryKey)

    override def * : ProvenShape[StoredTabulaAssignment] =
      (id, name, academicYear, created, version).mapTo[StoredTabulaAssignment]
  }

  class TabulaAssignmentVersions(tag: Tag) extends Table[StoredTabulaAssignmentVersion](tag, "tabula_assignment_version")
    with StoredVersionTable[StoredTabulaAssignment]
    with CommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    override def * : ProvenShape[StoredTabulaAssignmentVersion] =
      (id, name, academicYear, created, version, operation, timestamp, auditUser).mapTo[StoredTabulaAssignmentVersion]

    def pk = primaryKey("pk_announcement_version", (id, timestamp))
  }

  val tabulaAssignments: VersionedTableQuery[StoredTabulaAssignment, StoredTabulaAssignmentVersion, TabulaAssignments, TabulaAssignmentVersions] =
    VersionedTableQuery(TableQuery[TabulaAssignments], TableQuery[TabulaAssignmentVersions])

}

object TabulaAssignmentTables {
  case class StoredTabulaAssignment(
    id: UUID,
    name: String,
    academicYear: AcademicYear,
    created: OffsetDateTime,
    version: OffsetDateTime
  ) extends Versioned[StoredTabulaAssignment] {
    def asTabulaAssignment: TabulaAssignment =
      TabulaAssignment(
        id,
        name,
        academicYear,
      )
    override def atVersion(at: OffsetDateTime): StoredTabulaAssignment = copy(version = at)

    override def storedVersion[B <: StoredVersion[StoredTabulaAssignment]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredTabulaAssignmentVersion(
        id,
        name,
        academicYear,
        created,
        version,
        operation,
        timestamp,
        ac.usercode
      ).asInstanceOf[B]
  }

  case class StoredTabulaAssignmentVersion(
    id: UUID,
    name: String,
    academicYear: AcademicYear,
    created: OffsetDateTime,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredTabulaAssignment]
}

@ImplementedBy(classOf[TabulaAssignmentDaoImpl])
trait TabulaAssignmentDao {
  self: TabulaAssignmentTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def insert(tabulaAssignment: StoredTabulaAssignment)(implicit ac: AuditLogContext): DBIO[StoredTabulaAssignment]
  def getByIds(ids: Set[UUID]): DBIO[Seq[StoredTabulaAssignment]]
}

@Singleton
class TabulaAssignmentDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: PostgresCustomJdbcTypes
)(implicit ec: ExecutionContext) extends TabulaAssignmentDao with TabulaAssignmentTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
  import profile.api._

  override def insert(tabulaAssignment: StoredTabulaAssignment)(implicit ac: AuditLogContext): DBIO[StoredTabulaAssignment] =
    tabulaAssignments.insert(tabulaAssignment)

  override def getByIds(ids: Set[UUID]): DBIO[Seq[StoredTabulaAssignment]] =
    tabulaAssignments.table.filter(_.id inSetBind ids).result

}
