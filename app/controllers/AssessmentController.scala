package controllers

import java.util.UUID

import domain.StudentAssessmentWithAssessment
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService, StudentAssessmentService}

import scala.concurrent.ExecutionContext

@Singleton
class AssessmentController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
)( implicit
  ec: ExecutionContext,
) extends BaseController {
  import security._

  def view(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId) { implicit request =>
    Ok(views.html.exam.index(request.studentAssessmentWithAssessment))
  }

  def start(assessmentId: UUID): Action[AnyContent] = StudentAssessmentAction(assessmentId).async { implicit request =>
    studentAssessmentService.startAssessment(request.studentAssessmentWithAssessment.studentAssessment).successMap { studentAssessment =>
      Redirect(controllers.routes.AssessmentController.view(assessmentId))
    }
  }
}
