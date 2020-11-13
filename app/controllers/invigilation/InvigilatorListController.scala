package controllers.invigilation

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService, TimingInfoService}
import system.Roles

import scala.concurrent.ExecutionContext

@Singleton
class InvigilatorListController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  timingInfo: TimingInfoService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def list(): Action[AnyContent] = SigninRequiredAction.async { implicit req =>
    if(req.context.userHasRole(Roles.Admin)) {
      assessmentService.listWithStudentCount.successMap(assessments =>
        Ok(views.html.invigilation.list(assessments, timingInfo))
      )
    } else {
      val user = req.user.get // SigninRequired
      assessmentService.listForInvigilatorWithStudentCount(Set(user.usercode)).successMap(assessments =>
        Ok(views.html.invigilation.list(assessments, timingInfo))
      )
    }
  }

}

