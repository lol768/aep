package services.messaging

import actors.WebSocketActor.AssessmentMessage
import akka.Done
import domain.dao.{DaoRunner, MessageDao}
import domain.messaging.{Message, MessageSave, MessageSender}
import javax.inject.{Inject, Singleton}
import services.PubSubService
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService.GetStudentInformationOptions
import slick.dbio.DBIO
import system.routes.Types.UUID
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageService @Inject() (
  runner: DaoRunner,
  dao: MessageDao,
  pubSubService: PubSubService,
  studentInformationService: TabulaStudentInformationService
)(implicit ec: ExecutionContext) {

  /** Send a message. Currently only clients may message the staff, not vice versa. */
  def send(message: MessageSave, client: UniversityID, assessmentId: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[Message]] = {
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
  private def onSent(savedMessage: Message)(implicit tc: TimingContext): Future[ServiceResult[Done]] = {
    studentInformationService.getStudentInformation(GetStudentInformationOptions(savedMessage.client))
      .successMapTo(profile => s"${profile.fullName} (${profile.universityID.string})")
      .map(_.getOrElse(savedMessage.client.string))
      .map { clientName =>
        pubSubService.publish(
          topic = savedMessage.assessmentId.toString,
          AssessmentMessage(warwick.core.views.utils.nl2br(savedMessage.text).body, savedMessage.sender, clientName, savedMessage.created)
        )
        ServiceResults.success(Done)
      }
  }

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
