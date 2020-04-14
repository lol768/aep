package services.messaging

import akka.Done
import domain.AuditEvent.{Operation, Target}
import domain.messaging.{Message, MessageSave, MessageSender}
import domain.dao.{DaoRunner, MessageDao}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import slick.dbio.DBIO
import system.routes.Types.UUID
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageService @Inject() (
  auditService: AuditService,
  runner: DaoRunner,
  dao: MessageDao,
  userLookup: UserLookupService,
)(implicit ec: ExecutionContext) {

  /** Send a message. Currently only clients may message the staff, not vice versa. */
  def send(message: MessageSave, client: UniversityID, assessmentId: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[Message]] =
    auditService.audit(Operation.Assessment.SendQuery, assessmentId.toString, Target.Assessment, Json.obj("universityId" -> client.string)) {
      if (message.sender == MessageSender.Client) {
        runner.run(for {
          savedMessage <- dao.insert(message, client, assessmentId)
          r <- DBIO.from(onSent(savedMessage))
        } yield {
          r.map(_ => savedMessage)
        })
      } else {
        Future.successful(ServiceResults.error("Sending messages to clients not supported"))
      }
    }

  /** Called after a new message is persisted */
  private def onSent(savedMessage: Message): Future[ServiceResult[Done]] =
    // Can add additional hooks here later like posting to a pubsub topic to
    // update a websocket on a live invigilator dashboard
    Future.successful(Right(Done))

  def findById(id: UUID)(implicit ctx: TimingContext): Future[ServiceResult[Option[Message]]] =
    runner.run(dao.findById(id))
      .map(ServiceResults.success)

  def findByAssessment(assessmentId: UUID)(implicit ctx: TimingContext): Future[ServiceResult[Seq[Message]]] =
    runner.run(dao.forAssessment(assessmentId))
      .map(ServiceResults.success)

  def findByStudentAssessment(assessmentId: UUID, client: UniversityID)(implicit ctx: TimingContext): Future[ServiceResult[Seq[Message]]] =
    runner.run(dao.forStudentAssessment(assessmentId, client))
      .map(ServiceResults.success)

}
