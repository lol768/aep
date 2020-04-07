package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import controllers.{BaseController, FormMappings}
import controllers.admin.ViewStudentActivityController.{StudentActivityData, studentActivityForm}
import domain.{Assessment, AssessmentClientNetworkActivity, Pagination}
import javax.inject.{Inject, Singleton}
import services.{AssessmentClientNetworkActivityService, SecurityService, StudentAssessmentService}

import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Result}
import warwick.sso.{AuthenticatedRequest, UniversityID}

object ViewStudentActivityController {

  case class StudentActivityData(
    universityId: String,
    startDate: Option[OffsetDateTime] = None,
    endDate: Option[OffsetDateTime] = None,
  )

  val studentActivityForm = Form(mapping(
    "universityId" -> nonEmptyText,
    "startDate" -> optional(FormMappings.offsetDateTime),
    "endDate" -> optional(FormMappings.offsetDateTime)
  )(StudentActivityData.apply)(StudentActivityData.unapply))
}

@Singleton
class ViewStudentActivityController  @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
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
        studentAssessmentService.byUniversityId(UniversityID(data.universityId)).successFlatMap { assessments =>
          assessmentClientNetworkActivityService.getClientActivityFor(assessments.map(_.studentAssessment), data.startDate, data.endDate, Pagination.asPage(page, activityPerPage)).successFlatMap { case (total, activities) =>
            renderForm(
              studentActivityForm.bindFromRequest,
              assessments.map(a => a.studentAssessment.id -> a.assessment).toMap,
              activities, total, page, true)
          }
        }
      }
    )
  }

  private def renderForm(
    form: Form[StudentActivityData],
    assessments: Map[UUID, Assessment] = Map.empty,
    results: Seq[AssessmentClientNetworkActivity] = Seq.empty,
    total: Int = 0,
    page: Int = 0,
    showResults: Boolean = false
  )(implicit req: AuthenticatedRequest[_]): Future[Result] = {
    val pagination = Pagination(total, page, controllers.admin.routes.ViewStudentActivityController.filter(), activityPerPage)
    Future.successful(Ok(views.html.admin.studentActivity.activityForm(form, pagination, assessments, results, showResults)))
  }
}
