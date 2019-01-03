package controllers.sysadmin

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import services.SecurityService

@Singleton
class MasqueradeController @Inject()(
  securityService: SecurityService,
) extends BaseController {

  import securityService._

  def masquerade: Action[AnyContent] = RequireMasquerader { implicit request =>
    Ok(views.html.sysadmin.masquerade())
  }
}
