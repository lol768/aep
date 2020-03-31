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

  val blankForm: Form[MessageData] = Form(mapping(
    "messageText" -> nonEmptyText
  )(MessageData.apply)(MessageData.unapply))

  def showForm(assessmentId: UUID): Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    assessmentService.get(assessmentId).successMap { assessment =>
      Ok(views.html.assessment.messages(assessment, blankForm))
    }
  }

  def submitForm(assessmentId: UUID): Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    // This should actually do something once we have the back end set up.
    def success(data: MessageData) = Future.successful {
      Redirect(controllers.routes.MessageController.showForm(assessmentId))
        .flashing("success" -> Messages("flash.messages.sentToInvigilator"))
    }

    def failure(badForm: Form[MessageData]) =
      assessmentService.get(assessmentId).successMap { assessment =>
        BadRequest(views.html.assessment.messages(assessment, badForm))
      }

    blankForm.bindFromRequest().fold(failure, success)
  }
}

case class MessageData(
  messageText: String
)
