package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, StudentAssessmentService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessmentsController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def index: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    request.user.flatMap(_.universityId).map { universityId =>
      studentAssessmentService.byUniversityId(universityId).successMap { assessments =>
        Ok(views.html.exams.index(assessments))
      }
    }.getOrElse {
      Future.successful(Ok(views.html.exams.noUniversityId()))
    }
  }
}
