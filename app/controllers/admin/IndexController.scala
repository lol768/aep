package controllers.admin

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService}
import system.Roles

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class IndexController @Inject()(
  security: SecurityService,
  reportingService: AssessmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def home: Action[AnyContent] = GeneralDepartmentAdminAction { implicit request =>
    Ok(views.html.admin.home(canSeeReporting = request.context.userHasRole(Roles.Admin)))
  }
}
