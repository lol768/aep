package controllers

import javax.inject.Singleton
import play.api.mvc.{Action, AnyContent}

@Singleton
class IndexController extends BaseController {
  def home: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.home())
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY): Action[AnyContent] = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
