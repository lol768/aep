package services.job

import java.util.UUID

import javax.inject.Inject
import org.quartz._
import services.EmailService
import warwick.core.system.AuditLogContext

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Sends a single outgoing email for a particular user.
  */
@PersistJobDataAfterExecution
class SendOutgoingEmailJob @Inject()(
  emailService: EmailService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  def emailId(context: JobExecutionContext): UUID = {
    val dataMap = context.getJobDetail.getJobDataMap
    UUID.fromString(dataMap.getString("id"))
  }

  override val doLog: Boolean = false

  // Doesn't really matter, not used if doLog = false
  override def getDescription(context: JobExecutionContext): String = "Send Email"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] = {
    val id = emailId(context)
    try {
      emailService.get(id).flatMap {
        case Left(_) | Right(None) =>
          logger.info(s"OutgoingEmail $id no longer exists - ignoring")
          Future.unit

        case Right(Some(email)) =>
          emailService.sendImmediately(email).map(_.fold(
            errors => {
              val throwable = errors.flatMap(_.cause).headOption
              logger.error(s"Error sending email $id: ${errors.mkString(", ")}", throwable.orNull)
              rescheduleFor(30.seconds)
            },
            _ => {}
          ))
      }.map(_ =>
        JobResult.quiet
      )
    } catch {
      case t: Throwable =>
        logger.error(s"Error sending outgoing email $id - retrying in 30 seconds", t)
        rescheduleFor(30.seconds)
        Future.successful(JobResult.quiet)
    }
  }

}
