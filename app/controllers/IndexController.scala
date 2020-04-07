package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IndexController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  assessmentService: AssessmentService,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import security._

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    val userRoles = currentUserRoles()

    val checkRedirectToAssessmentsView: Future[ServiceResult[Boolean]] =
      if (userRoles.isStudent || userRoles.isPGR) Future.successful(ServiceResults.success(true))
      else userRoles.user.universityId.map { universityId =>
        studentAssessmentService.byUniversityId(universityId).map(_.map(_.nonEmpty))
      }.getOrElse(Future.successful(ServiceResults.success(true)))

    lazy val isInvigilator = assessmentService.isInvigilator(currentUser().usercode)

    lazy val isAdmin = userRoles.isAdmin || userRoles.isSysAdmin

    // Redirect if admin, invigilator or examinee
    // (if both admin and invigilator then we go to admin)

    // nesting nightmare because of mix of bools, futures and serviceresults
    checkRedirectToAssessmentsView.successFlatMap {
      case true => Future.successful(Redirect(controllers.routes.AssessmentsController.index()))
      case _ =>
        if (isAdmin) {
          Future.successful(Redirect(controllers.admin.routes.IndexController.home()))
        } else {
          isInvigilator.successMap { invigilator =>
            if (invigilator) {
              Redirect(controllers.invigilation.routes.InvigilatorListController.list())
            } else {
              Ok(views.html.homeNoRoles(userRoles))
            }
          }
        }
    }
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY): Action[AnyContent] = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
