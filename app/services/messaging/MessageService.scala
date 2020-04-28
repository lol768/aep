package services.messaging

import java.util.UUID

import actors.WebSocketActor.AssessmentMessage
import akka.Done
import controllers.WebSocketController.Topics
import domain.AuditEvent.{Operation, Target}
import domain.{AssessmentMetadata, DepartmentCode}
import domain.dao.{DaoRunner, MessageDao}
import domain.messaging.{Message, MessageSave, MessageSender}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import services.{NotificationService, PubSubService}
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService.GetStudentInformationOptions
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

object MessageService {
  val InvigilationSender = "Invigilation team"
}

@Singleton
class MessageService @Inject() (
  auditService: AuditService,
  runner: DaoRunner,
  dao: MessageDao,
  pubSubService: PubSubService,
  studentInformationService: TabulaStudentInformationService,
  notificationService: NotificationService,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) {

  def send(message: MessageSave, student: UniversityID, assessmentId: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[Message]] =
    auditService.audit(Operation.Assessment.SendQuery, assessmentId.toString, Target.Assessment, Json.obj("universityId" -> student.string)) {
      runner.run(for {
        savedMessage <- dao.insert(message.toMessage(student, assessmentId))
        r <- DBIO.from(onSent(savedMessage))
      } yield {
        r.map(_ => savedMessage)
      })
    }

  /** Called after a new message is persisted */
  private def onSent(savedMessage: Message)(implicit tc: TimingContext): Future[ServiceResult[Done]] = {
    studentInformationService.getStudentInformation(GetStudentInformationOptions(savedMessage.student))
      .successMapTo(profile => s"${profile.fullName} (${profile.universityID.string})")
      .map(_.getOrElse(savedMessage.student.string))
      .map { studentName =>
        savedMessage.sender match {
          case MessageSender.Student =>
            pubSubService.publish(
              topic = Topics.allInvigilatorsAssessment(savedMessage.assessmentId),
              AssessmentMessage(savedMessage.id.toString, savedMessage.student.string, savedMessage.assessmentId.toString, savedMessage.text, savedMessage.sender, studentName, studentName, savedMessage.created)
            )
            notificationService.newMessageFromStudent(savedMessage)
            ServiceResults.success(Done)
          case MessageSender.Invigilator =>
            // Send to other invigilators (but don't notify)
            val invigilatorName = userLookupService.getUser(savedMessage.staffId.get).toOption.flatMap(_.name.full).getOrElse(s"[Unknown user (${savedMessage.staffId.get})]")
            pubSubService.publish(
              topic = Topics.allInvigilatorsAssessment(savedMessage.assessmentId),
              AssessmentMessage(savedMessage.id.toString, savedMessage.student.string, savedMessage.assessmentId.toString, savedMessage.text, savedMessage.sender, invigilatorName, studentName, savedMessage.created)
            )

            // Send to specific student
            pubSubService.publish(
              topic = Topics.studentAssessment(savedMessage.student)(savedMessage.assessmentId),
              AssessmentMessage(savedMessage.id.toString, savedMessage.student.string, savedMessage.assessmentId.toString, savedMessage.text, savedMessage.sender, MessageService.InvigilationSender, studentName, savedMessage.created)
            )
            notificationService.newMessageFromInvigilator(savedMessage)
            ServiceResults.success(Done)
        }
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

  def findByDepartmentCode(departmentCode: DepartmentCode)(implicit ctx: TimingContext): Future[ServiceResult[Seq[(AssessmentMetadata, Message)]]] =
    runner.run(dao.findByDepartmentCode(departmentCode))
      .map( _.map {
        case (assessment, message) => (assessment.asAssessmentMetadata, message)
      })
      .map(ServiceResults.success)

}
