package controllers.invigilation

import controllers.BaseController
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService}

import scala.concurrent.ExecutionContext

class InvigilatorListController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def list(): Action[AnyContent] = SigninRequiredAction.async { implicit req =>
    val user = req.user.get // SigninRequired
    assessmentService.listForInvigilator(Set(user.usercode)).successMap(assessments =>
      Ok(views.html.invigilation.list(assessments))
    )
  }

}

