package domain.dao

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.ImplementedBy
import domain._
import domain.dao.OutgoingEmailsTables._
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.Email
import play.db.NamedDatabase
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

import scala.concurrent.ExecutionContext

trait OutgoingEmailsTables extends VersionedTables {
  self: HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  val jdbcTypes: CustomJdbcTypes
  import jdbcTypes._

  trait OutgoingEmailCommonProperties { self: Table[_] =>
    def created = column[OffsetDateTime]("created_utc")
    def email = column[JsValue]("email")
    def recipient = column[Option[Usercode]]("recipient")
    def emailAddress = column[Option[String]]("recipient_email")
    def sent = column[Option[OffsetDateTime]]("sent_at_utc")
    def lastSendAttempt = column[Option[OffsetDateTime]]("last_send_attempt_at_utc")
    def failureReason = column[Option[String]]("failure_reason")
    def version = column[OffsetDateTime]("version_utc")
  }

  class OutgoingEmails(tag: Tag) extends Table[StoredOutgoingEmail](tag, "outgoing_email") with VersionedTable[StoredOutgoingEmail] with OutgoingEmailCommonProperties {
    override def matchesPrimaryKey(other: StoredOutgoingEmail): Rep[Boolean] = id === other.id

    def id = column[UUID]("id", O.PrimaryKey)
    def isSent: Rep[Boolean] = sent.nonEmpty
    def isQueued: Rep[Boolean] = !isSent && (lastSendAttempt.nonEmpty || failureReason.isEmpty)

    def * = (id, created, email, recipient, emailAddress, sent, lastSendAttempt, failureReason, version).mapTo[StoredOutgoingEmail]
  }

  class OutgoingEmailVersions(tag: Tag) extends Table[StoredOutgoingEmailVersion](tag, "outgoing_email_version") with StoredVersionTable[StoredOutgoingEmail] with OutgoingEmailCommonProperties {
    def id = column[UUID]("id")
    def operation = column[DatabaseOperation]("version_operation")
    def timestamp = column[OffsetDateTime]("version_timestamp_utc")
    def auditUser = column[Option[Usercode]]("version_user")

    def * = (id, created, email, recipient, emailAddress, sent, lastSendAttempt, failureReason, version, operation, timestamp, auditUser).mapTo[StoredOutgoingEmailVersion]
    def pk = primaryKey("pk_outgoing_email_version", (id, timestamp))
    def idx = index("idx_outgoing_email_version", (id, version))
  }

  val outgoingEmails: VersionedTableQuery[StoredOutgoingEmail, StoredOutgoingEmailVersion, OutgoingEmails, OutgoingEmailVersions] =
    VersionedTableQuery(TableQuery[OutgoingEmails], TableQuery[OutgoingEmailVersions])
}

object OutgoingEmailsTables {
  case class StoredOutgoingEmail(
    id: UUID,
    created: OffsetDateTime,
    email: JsValue,
    recipient: Option[Usercode],
    emailAddress: Option[String],
    sent: Option[OffsetDateTime],
    lastSendAttempt: Option[OffsetDateTime],
    failureReason: Option[String],
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[StoredOutgoingEmail] {
    override def atVersion(at: OffsetDateTime): StoredOutgoingEmail = copy(version = at)
    override def storedVersion[B <: StoredVersion[StoredOutgoingEmail]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      StoredOutgoingEmailVersion.versioned(this, operation, timestamp).asInstanceOf[B]

    def parsed: OutgoingEmail = OutgoingEmail(
      Some(id),
      created,
      email.validate[Email](OutgoingEmail.emailFormatter).get,
      recipient,
      emailAddress,
      sent,
      lastSendAttempt,
      failureReason,
      version
    )
  }

  case class StoredOutgoingEmailVersion(
    id: UUID,
    created: OffsetDateTime,
    email: JsValue,
    recipient: Option[Usercode],
    emailAddress: Option[String],
    sent: Option[OffsetDateTime],
    lastSendAttempt: Option[OffsetDateTime],
    failureReason: Option[String],
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[StoredOutgoingEmail]

  object StoredOutgoingEmailVersion {
    def tupled = (apply _).tupled

    def versioned(email: StoredOutgoingEmail, operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): StoredOutgoingEmailVersion =
      StoredOutgoingEmailVersion(
        email.id,
        email.created,
        email.email,
        email.recipient,
        email.emailAddress,
        email.sent,
        email.lastSendAttempt,
        email.failureReason,
        email.version,
        operation,
        timestamp,
        ac.usercode
      )
  }
}

@ImplementedBy(classOf[OutgoingEmailDaoImpl])
trait OutgoingEmailDao {
  self: OutgoingEmailsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] =>

  import profile.api._

  def insert(email: OutgoingEmail)(implicit ac: AuditLogContext): DBIO[StoredOutgoingEmail]
  def insertAll(emails: Seq[OutgoingEmail])(implicit ac: AuditLogContext): DBIO[Seq[StoredOutgoingEmail]]
  def update(email: OutgoingEmail, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredOutgoingEmail]
  def get(id: UUID): DBIO[Option[StoredOutgoingEmail]]
  def allUnsentEmails(): DBIO[Seq[StoredOutgoingEmail]]
  def countUnsentEmails(): DBIO[Int]
  def oldestUnsentEmail(): DBIO[Option[StoredOutgoingEmail]]
  def mostRecentlySentEmail(): DBIO[Option[StoredOutgoingEmail]]
  def getEmails(offset: Int, numberToReturn: Int): DBIO[Seq[StoredOutgoingEmail]]
  def countEmails(): DBIO[Int]
  def getEmailsSentTo(emailAddresses: Seq[String], startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime], offset: Int, numberToReturn: Int): DBIO[Seq[StoredOutgoingEmail]]
  def countEmailsSentTo(emailAddresses: Seq[String],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]): DBIO[Int]
}

@Singleton
class OutgoingEmailDaoImpl @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider,
  val jdbcTypes: CustomJdbcTypes,
)(implicit executionContext: ExecutionContext) extends OutgoingEmailDao with OutgoingEmailsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {

  import dbConfig.profile.api._

  override def insert(email: OutgoingEmail)(implicit ac: AuditLogContext): DBIO[StoredOutgoingEmail] =
    outgoingEmails.insert(StoredOutgoingEmail(
      UUID.randomUUID(),
      email.created,
      Json.toJson(email.email)(OutgoingEmail.emailFormatter),
      email.recipient,
      email.emailAddress,
      email.sent,
      email.lastSendAttempt,
      email.failureReason
    ))


  override def insertAll(emails: Seq[OutgoingEmail])(implicit ac: AuditLogContext): DBIO[Seq[StoredOutgoingEmail]] =
    outgoingEmails.insertAll(emails.map { email =>
      StoredOutgoingEmail(
        UUID.randomUUID(),
        email.created,
        Json.toJson(email.email)(OutgoingEmail.emailFormatter),
        email.recipient,
        email.emailAddress,
        email.sent,
        email.lastSendAttempt,
        email.failureReason
      )
    })

  override def update(email: OutgoingEmail, version: OffsetDateTime)(implicit ac: AuditLogContext): DBIO[StoredOutgoingEmail] =
    outgoingEmails.update(StoredOutgoingEmail(
      email.id.get,
      email.created,
      Json.toJson(email.email)(OutgoingEmail.emailFormatter),
      email.recipient,
      email.emailAddress,
      email.sent,
      email.lastSendAttempt,
      email.failureReason,
      version
    ))

  override def get(id: UUID): DBIO[Option[StoredOutgoingEmail]] =
    outgoingEmails.table.filter(_.id === id).take(1).result.headOption

  override def allUnsentEmails(): DBIO[Seq[StoredOutgoingEmail]] =
    outgoingEmails.table.filter(_.isQueued).sortBy(_.created).result

  override def countUnsentEmails(): DBIO[Int] =
    outgoingEmails.table.filter(_.isQueued).length.result

  override def oldestUnsentEmail(): DBIO[Option[StoredOutgoingEmail]] =
    outgoingEmails.table
      .filter(_.isQueued)
      .sortBy(_.created)
      .take(1)
      .result
      .headOption

  override def mostRecentlySentEmail(): DBIO[Option[StoredOutgoingEmail]] =
    outgoingEmails.table
      .filter(_.isSent)
      .sortBy(_.created.desc)
      .take(1)
      .result
      .headOption

  override def getEmails(offset: Int, numberToReturn: Int): DBIO[Seq[StoredOutgoingEmail]] =
    outgoingEmails.table
      .filter(_.isSent)
      .sortBy(_.sent.desc)
      .drop(offset)
      .take(numberToReturn)
      .result

  override def countEmails(): DBIO[Int] =
    outgoingEmails.table
      .filter(_.isSent)
      .length
      .result

  override def getEmailsSentTo(emailAddresses:Seq[String], startDateOpt:Option[OffsetDateTime], endDateOpt:Option[OffsetDateTime], offset: Int, numberToReturn: Int): DBIO[Seq[StoredOutgoingEmail]] =
    emailsSentToFilterQuery(emailAddresses, startDateOpt, endDateOpt)
      .sortBy(_.sent.desc)
      .drop(offset)
      .take(numberToReturn)
      .result

  override def countEmailsSentTo(emailAddresses:Seq[String], startDateOpt:Option[OffsetDateTime], endDateOpt:Option[OffsetDateTime]): DBIO[Int] = {
    emailsSentToFilterQuery(emailAddresses, startDateOpt, endDateOpt).length.result
  }

  private def emailsSentToFilterQuery(emailAddresses: Seq[String],startDateOpt: Option[OffsetDateTime], endDateOpt: Option[OffsetDateTime]) = {
    outgoingEmails.table
      .filter(_.isSent)
      .filter{e =>
        if (emailAddresses.nonEmpty) { e.emailAddress.map(_.toLowerCase.inSet(emailAddresses.map(_.toLowerCase))).getOrElse(false) } else { LiteralColumn(true) }  &&
        startDateOpt.map { startDate => e.sent.map(d => d > startDate).getOrElse(true)}.getOrElse(LiteralColumn(true)) &&
        endDateOpt.map { endDate => e.sent.map(d => d <= endDate).getOrElse(true)}.getOrElse(LiteralColumn(true))
      }
  }
}
