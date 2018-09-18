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

@ImplementedBy(classOf[AuditDaoImpl])
trait AuditDao {
  import slick.dbio._
  def insert(event: AuditEvent): DBIO[AuditEvent]
}

@Singleton
class AuditDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  jdbcTypes: CustomJdbcTypes
)(implicit ec: ExecutionContext) extends AuditDao with HasDatabaseConfigProvider[JdbcProfile] {
  import profile.api._
  import jdbcTypes._

  /**
    * Table classes defined inside a DAO (or other instance with access to the Slick profile)
    * because column definitions depend on profile.api._
    */
  class AuditEvents(tag: Tag) extends Table[AuditEvent](tag, "audit_event") {
    def id = column[UUID]("id", O.PrimaryKey)
    def date = column[OffsetDateTime]("event_date_utc")
    def operation = column[String]("operation")
    def usercode = column[Usercode]("usercode")
    def data = column[JsValue]("data")
    def targetId = column[String]("target_id")
    def targetType = column[String]("target_type")

    def * = (id, date, operation, usercode.?, data, targetId, targetType).mapTo[AuditEvent]
  }

  // If you want to share TableQuerys between DAOs, it is fine to expose these from the trait.
  val auditEvents = TableQuery[AuditEvents]

  override def insert(event: AuditEvent): DBIO[AuditEvent] = {
    (auditEvents += event).map(_ => event)
  }
}
