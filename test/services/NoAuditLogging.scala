package services

import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

/** For a trait that expects these implicits */
trait AuditLoggingDeclarations {
  protected implicit def timingContext: TimingContext
  protected implicit val auditLogContext: AuditLogContext
}

trait NoAuditLogging extends AuditLoggingDeclarations {

  protected implicit def timingContext: TimingContext = TimingContext.none
  protected implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()

}
