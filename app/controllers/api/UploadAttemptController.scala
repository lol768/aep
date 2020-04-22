package controllers.api

import controllers.BaseController
import domain.UploadAttempt
import javax.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import play.api.mvc.Action
import services.UploadAttemptService

@Singleton
class UploadAttemptController @Inject()(
  uploadAttemptService: UploadAttemptService
) extends BaseController {

  import UploadAttempt._

  def log: Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[UploadAttempt].fold(
      invalid => BadRequest(invalid.toString()),
      attempt => {
        attempt.source = "HTTP"
        uploadAttemptService.logAttempt(attempt)
        Ok("")
      }
    )
  }

}
