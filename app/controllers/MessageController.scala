package controllers

import java.util.UUID

import javax.inject.{Inject, Singleton}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService, StudentAssessmentService}
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import security._
  import MessageController._

  def showForm(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId) { implicit request =>
    Ok(views.html.assessment.messages(request.studentAssessmentWithAssessment.assessment, blankForm))
  }

  def submitForm(assessmentId: UUID): Action[AnyContent] = StudentAssessmentInProgressAction(assessmentId) { implicit request =>
    // This should actually do something once we have the back end set up.
    def success(data: MessageData) =
      Redirect(controllers.routes.MessageController.showForm(assessmentId))
        .flashing("success" -> Messages("flash.messages.sentToInvigilator"))

    def failure(badForm: Form[MessageData]) =
      BadRequest(views.html.assessment.messages(request.studentAssessmentWithAssessment.assessment, badForm))

    blankForm.bindFromRequest().fold(failure, success)
  }
}

object MessageController {
  case class MessageData(
    messageText: String
  )

  val blankForm: Form[MessageData] = Form(mapping(
    "messageText" -> nonEmptyText
  )(MessageData.apply)(MessageData.unapply))
}


