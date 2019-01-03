package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{AuditEvent, CustomJdbcTypes}
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait AuditEventsTable {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  val jdbcTypes: CustomJdbcTypes
  import jdbcTypes._

  /**
    * Table classes defined inside a DAO (or other instance with access to the Slick profile)
    * because column definitions depend on profile.api._
    */
  class AuditEvents(tag: Tag) extends Table[AuditEvent](tag, "audit_event") {
    def id = column[UUID]("id", O.PrimaryKey)
    def date = column[OffsetDateTime]("event_date_utc")
    def operation = column[Symbol]("operation")
    def usercode = column[Usercode]("usercode")
    def data = column[JsValue]("data")
    def targetId = column[String]("target_id")
    def targetType = column[Symbol]("target_type")

    def * = (id, date, operation, usercode.?, data, targetId, targetType).mapTo[AuditEvent]
  }

  val auditEvents = TableQuery[AuditEvents]
}

@ImplementedBy(classOf[AuditDaoImpl])
trait AuditDao {
  self: AuditEventsTable with HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  def insert(event: AuditEvent): DBIO[AuditEvent]
  def getById(id: UUID): DBIO[Option[AuditEvent]]
  def findByOperationAndUsercodeQuery(operation: Symbol, usercode: Usercode): Query[AuditEvents, AuditEvent, Seq]
}

@Singleton
class AuditDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: CustomJdbcTypes
)(implicit ec: ExecutionContext) extends AuditDao with AuditEventsTable with HasDatabaseConfigProvider[JdbcProfile] {
  import profile.api._
  import jdbcTypes._

  override def insert(event: AuditEvent): DBIO[AuditEvent] =
    (auditEvents += event).map(_ => event)

  override def getById(id: UUID): DBIO[Option[AuditEvent]] =
    auditEvents.filter(_.id === id).result.headOption

  override def findByOperationAndUsercodeQuery(operation: Symbol, usercode: Usercode): Query[AuditEvents, AuditEvent, Seq] =
    auditEvents.filter { ae => ae.operation === operation && ae.usercode === usercode }
}
