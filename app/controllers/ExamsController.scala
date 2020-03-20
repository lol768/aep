package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.SecurityService

@Singleton
class ExamsController @Inject()(
  security: SecurityService,
) extends BaseController {
  import security._

  def index: Action[AnyContent] = SigninAwareAction { implicit request =>
    Ok(views.html.exams.index())
  }
}
