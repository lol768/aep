package controllers.invigilation

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService}
import system.Roles

import scala.concurrent.ExecutionContext

@Singleton
class InvigilatorListController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def list(): Action[AnyContent] = SigninRequiredAction.async { implicit req =>
    if(req.context.userHasRole(Roles.Admin)) {
      assessmentService.list.successMap(assessments =>
        Ok(views.html.invigilation.list(assessments))
      )
    } else {
      val user = req.user.get // SigninRequired
      assessmentService.listForInvigilator(Set(user.usercode)).successMap(assessments =>
        Ok(views.html.invigilation.list(assessments))
      )
    }
  }

}

