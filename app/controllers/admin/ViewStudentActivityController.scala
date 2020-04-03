package controllers.admin

import java.time.OffsetDateTime

import controllers.{BaseController, FormMappings}
import controllers.admin.ViewStudentActivityController.{StudentActivityData, studentActivityForm}
import domain.{AssessmentClientNetworkActivity, Pagination}
import javax.inject.{Inject, Singleton}
import services.{AssessmentClientNetworkActivityService, SecurityService, StudentAssessmentService}
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import warwick.sso.{AuthenticatedRequest, UserLookupService, Usercode}

object ViewStudentActivityController {

  case class StudentActivityData(
    usercode: String,
    startDate: Option[OffsetDateTime] = None,
    endDate: Option[OffsetDateTime] = None,
  )

  val studentActivityForm = Form(mapping(
    "usercode" -> nonEmptyText,
    "startDate" -> optional(FormMappings.offsetDateTime),
    "endDate" -> optional(FormMappings.offsetDateTime)
  )(StudentActivityData.apply)(StudentActivityData.unapply))
}

@Singleton
class ViewStudentActivityController  @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  userLookupService: UserLookupService,
  assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def activityPerPage: Int = 120

  def index: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    renderForm(studentActivityForm)
  }

  def filter(page: Int = 0): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    studentActivityForm.bindFromRequest.fold(
      errors => renderForm(errors),
      data => {
        userLookupService.getUser(Usercode(data.usercode)).map{ user =>
          user.universityId.map{ universityId =>
            studentAssessmentService.byUniversityId(universityId).successFlatMap{ assessments =>
              assessmentClientNetworkActivityService.getClientActivityFor(assessments.map(_.studentAssessment), data.startDate, data.endDate, Pagination.asPage(page, activityPerPage)).successFlatMap{ case (total, activities) =>
                renderForm(studentActivityForm.bindFromRequest, activities, total, page, true)
              }
            }
          }.getOrElse(
            renderForm(form=studentActivityForm, flash=Map("error" -> Messages("flash.missing.universityId")))
          )
        }.getOrElse(
          renderForm(form=studentActivityForm, flash=Map("error" -> Messages("flash.invalid.usercode", data.usercode)))
        )
      }
    )
  }

  private def renderForm(
    form: Form[StudentActivityData],
    results: Seq[AssessmentClientNetworkActivity] = Seq.empty,
    total: Int = 0,
    page: Int = 0,
    showResults: Boolean = false,
    flash: Map[String,String] = Map.empty
  )(implicit req: AuthenticatedRequest[_]): Future[Result] = {
    val pagination = Pagination(total, page, controllers.admin.routes.ViewStudentActivityController.filter(), activityPerPage)
    val activityForm = Ok(views.html.admin.studentActivity.activityForm(form, pagination, results, showResults))
    val result = if(flash.nonEmpty) activityForm.flashing(flash.head) else activityForm
    Future.successful(result)
  }
}
