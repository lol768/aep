package controllers.admin

import java.time.{Duration, LocalDateTime}
import java.util.UUID

import controllers.BaseController
import domain.Assessment
import domain.Assessment.{AssessmentType, Brief, Platform, State}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services.{AssessmentService, SecurityService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

object AssessmentsController {

  case class AssessmentFormData(
    title: String,
    description: Option[String],
    durationMinutes: Long,
    platform: Platform,
    assessmentType: AssessmentType,
    url: Option[String]
  )

  val form: Form[AssessmentFormData] = Form(mapping(
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> longNumber(min = 1, max = 24 * 60),
    "platform" -> Platform.formField,
    "assessmentType" -> AssessmentType.formField,
    "url" -> optional(nonEmptyText)
  )(AssessmentFormData.apply)(AssessmentFormData.unapply))

  case class AdHocAssessmentFormData(
    moduleCode: String,
    startTime: LocalDateTime,
    invigilators: Set[Usercode],
    title: String,
    description: Option[String],
    durationMinutes: Long,
    platform: Platform,
    assessmentType: AssessmentType,
    url: Option[String]
  )

  val adHocAssessmentForm: Form[AdHocAssessmentFormData] = Form(mapping(
    "moduleCode" -> nonEmptyText,
    "startTime" -> nonEmptyText.transform[LocalDateTime](LocalDateTime.parse(_), _.toString),
    "invigilators" -> set(optional(text))
      .transform[Set[String]](_.flatten, _.map(Option.apply))
      .transform[Set[Usercode]](_.map(Usercode), _.map(_.string)),
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> longNumber(min = 1, max = 24 * 60),
    "platform" -> Platform.formField,
    "assessmentType" -> AssessmentType.formField,
    "url" -> optional(nonEmptyText)
  )(AdHocAssessmentFormData.apply)(AdHocAssessmentFormData.unapply))
}

@Singleton
class AssessmentsController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  uploadedFileService: UploadedFileService
)(implicit ec: ExecutionContext) extends BaseController {

  import AssessmentsController._
  import security._

  def index: Action[AnyContent] = RequireDepartmentAssessmentManager.async { implicit request =>
    assessmentService.findByStates(Seq(State.Draft)).successMap { assessments =>
      Ok(views.html.admin.assessments.index(assessments))
    }
  }

  def show(id: UUID): Action[AnyContent] = RequireDepartmentAssessmentManager.async { implicit request =>
    assessmentService.get(id).successMap { assessment =>
      Ok(views.html.admin.assessments.show(assessment, form.fill(AssessmentFormData(
        title = assessment.title,
        description = assessment.brief.text,
        durationMinutes = assessment.duration.toMinutes,
        platform = assessment.platform,
        assessmentType = assessment.assessmentType,
        url = assessment.brief.url
      ))))
    }
  }

  def create(): Action[AnyContent] = RequireDepartmentAssessmentManager { implicit request =>
    Ok(views.html.admin.assessments.create(adHocAssessmentForm))
  }

  def save(): Action[MultipartFormData[TemporaryUploadedFile]] = RequireDepartmentAssessmentManager(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    adHocAssessmentForm.bindFromRequest().fold(
      formWithErrors => Future.successful(Ok(views.html.admin.assessments.create(formWithErrors))),
      data => {
        import helpers.DateConversion._

        val files = request.body.files.map(_.ref)

        assessmentService.insert(
          Assessment(
            code = data.moduleCode,
            title = data.title,
            startTime = Option(data.startTime).map(_.asOffsetDateTime),
            duration = Duration.ofMinutes(data.durationMinutes),
            platform = data.platform,
            assessmentType = data.assessmentType,
            brief = Brief(
              text = data.description,
              url = data.url,
              files = Nil,
            ),
            invigilators = data.invigilators,
            state = State.Submitted,
          ),
          files = files.map(f => (f.in, f.metadata))
        ).successMap { _ =>
          Redirect(routes.AssessmentsController.index()).flashing("success" -> Messages("flash.files.uploaded", files.size))
        }
      })
  }

  def update(id: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = RequireDepartmentAssessmentManager(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    assessmentService.get(id).successFlatMap { assessment =>
      form.bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.assessments.show(assessment, formWithErrors))),
        data => {
          val files = request.body.files.map(_.ref)
          assessmentService.update(assessment.copy(
            title = data.title,
            duration = Duration.ofMinutes(data.durationMinutes),
            platform = data.platform,
            assessmentType = data.assessmentType,
            brief = assessment.brief.copy(
              text = data.description,
              url = data.url
            ),
            state = State.Submitted
          ), files = files.map(f => (f.in, f.metadata))).successMap { _ =>
            Redirect(routes.AssessmentsController.index()).flashing("success" -> Messages("flash.files.uploaded", files.size))
          }
        })
    }
  }

  def getFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = RequireDepartmentAssessmentManager.async { implicit request =>
    assessmentService.get(assessmentId).successFlatMap { assessment =>
      assessment.brief.files.find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound("File not found")))
    }
  }
}
