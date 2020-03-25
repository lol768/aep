package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IndexController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import security._

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val user = currentUser()

    val checkRedirectToAssessmentsView: Future[ServiceResult[Boolean]] =
      if (user.isStudent || user.isPGR) Future.successful(ServiceResults.success(true))
      else user.universityId.map { universityId =>
        studentAssessmentService.byUniversityId(universityId).map(_.map(_.nonEmpty))
      }.getOrElse(Future.successful(ServiceResults.success(true)))

    checkRedirectToAssessmentsView.successMap {
      case true => Redirect(controllers.routes.AssessmentsController.index())
      case _ =>
        // At some point in the future we may redirect somewhere useful for staff
        Ok(views.html.home())
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY): Action[AnyContent] = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
