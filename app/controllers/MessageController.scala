package controllers

import java.time.OffsetDateTime
import java.util.UUID

import domain.messaging.{MessageSave, MessageSender}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.SecurityService
import services.messaging.MessageService
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService.GetStudentInformationOptions
import warwick.core.helpers.ServiceResults

import scala.concurrent.ExecutionContext

@Singleton
class MessageController @Inject()(
  security: SecurityService,
  configuration: Configuration,
  messageService: MessageService,
  studentInformationService: TabulaStudentInformationService,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import MessageController._
  import security._

  private lazy val messageMaxLength: Int = configuration.get[Int]("app.messageMaxLength")

  def showForm(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId, allowWhereNoDuration = true).async { implicit request =>
    val universityId = request.sitting.studentAssessment.studentId
    ServiceResults.zip(
      messageService.findByStudentAssessment(assessmentId, universityId),
      studentInformationService.getStudentInformation(GetStudentInformationOptions(universityId))
    ).successMap { case (messages, profile) =>
      val student = Map(universityId -> profile)
      val sortedMessages = messages.sortBy(_.created)(Ordering[OffsetDateTime].reverse)
      Ok(views.html.assessment.messages(request.sitting.assessment, sortedMessages, student, blankForm(messageMaxLength)))
    }
  }

  def submitForm(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId, allowWhereNoDuration = true).async { implicit request =>
    def success(data: MessageData) =
      messageService.send(MessageSave(data.messageText, MessageSender.Student, None), currentUniversityId(), assessmentId).successMap { _ =>
        Redirect(controllers.routes.MessageController.showForm(assessmentId))
          .flashing("success" -> Messages("flash.messages.sentToInvigilator"))
      }


    def failure(badForm: Form[MessageData]) = {
      val universityId = request.sitting.studentAssessment.studentId
      ServiceResults.zip(
        messageService.findByStudentAssessment(assessmentId, universityId),
        studentInformationService.getStudentInformation(GetStudentInformationOptions(universityId))
      ).successMap { case (messages, profile) =>
        val student = Map(universityId -> profile)
        val sortedMessages = messages.sortBy(_.created)(Ordering[OffsetDateTime].reverse)
        BadRequest(views.html.assessment.messages(request.sitting.assessment, sortedMessages, student, blankForm(messageMaxLength)))
      }
    }

    blankForm(messageMaxLength).bindFromRequest().fold(failure, success)
  }
}

object MessageController {
  case class MessageData(
    messageText: String
  )

  def blankForm(maxLength: Int): Form[MessageData] = Form(mapping(
    "messageText" -> nonEmptyText(maxLength = maxLength)
  )(MessageData.apply)(MessageData.unapply))
}


