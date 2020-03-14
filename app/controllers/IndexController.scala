package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.SecurityService

@Singleton
class IndexController @Inject()(
  security: SecurityService,
) extends BaseController {
  import security._

  def home: Action[AnyContent] = SigninAwareAction { implicit request =>
    Ok(views.html.home())
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY): Action[AnyContent] = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
