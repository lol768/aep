package controllers.admin

import java.time.OffsetDateTime

import controllers.BaseController
import controllers.admin.ViewStudentActivityController.{StudentActivityData, sudentActivityForm}
import javax.inject.{Inject, Singleton}
import services.SecurityService

import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.mvc.{Action, AnyContent}
import warwick.core.helpers.JavaTime

object ViewStudentActivityController {

  case class StudentActivityData(
    usercode: String,
    startDate: Option[OffsetDateTime] = None,
    endDate: Option[OffsetDateTime] = None,
  )

  val sudentActivityForm = Form(mapping(
    "usercode" -> nonEmptyText,
    "startDate" -> optional(JavaTime.offsetDateTimeFormField),
    "endDate" -> optional(JavaTime.offsetDateTimeFormField)
  )(StudentActivityData.apply)(StudentActivityData.unapply))
}

@Singleton
class ViewStudentActivityController  @Inject()(
  security: SecurityService,

)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def index: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    Future.successful(Ok(views.html.admin.studentActivity.activityForm(sudentActivityForm)))
  }

  def filter: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    val form = sudentActivityForm.bindFromRequest()
    val studentActivityData = form.value.getOrElse(StudentActivityData)

    Future.successful(Ok(views.html.admin.studentActivity.activityForm(form)))
  }
}
