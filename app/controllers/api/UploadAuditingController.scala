package controllers.api

import controllers.BaseController
import domain.{UploadAttempt, UploadCancellation}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import play.api.mvc.Action
import services.UploadAuditingService

@Singleton
class UploadAuditingController @Inject()(
  uploadAuditingService: UploadAuditingService
) extends BaseController {

  import UploadAttempt._

  def logAttempt: Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[UploadAttempt].fold(
      invalid => BadRequest(invalid.toString()),
      attempt => {
        attempt.source = "HTTP"
        uploadAuditingService.logAttempt(attempt)
        Ok("")
      }
    )
  }

  def logCancellation: Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[UploadCancellation].fold(
      invalid => BadRequest(invalid.toString()),
      cancellation => {
        uploadAuditingService.logCancellation(cancellation.id)
        Ok("")
      }
    )
  }
}
