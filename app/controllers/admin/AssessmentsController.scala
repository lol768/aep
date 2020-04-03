package controllers.admin

import java.time.{Duration, LocalDateTime}
import java.util.UUID

import controllers.BaseController
import domain.{Assessment, DepartmentCode}
import domain.Assessment.{AssessmentType, Brief, Platform, State}
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services.{AssessmentService, SecurityService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

object AssessmentsController {
  import controllers.admin.AssessmentsController.AbstractAssessmentFormData._

  trait AbstractAssessmentFormData {
    val moduleCode: String

    val paperCode: String

    val section: Option[String]

    val departmentCode: DepartmentCode

    val sequence: String

    val startTime: Option[LocalDateTime] = None

    val invigilators: Option[Set[Usercode]]

    val title: String

    val description: Option[String]

    val durationMinutes: Long

    val platform: Platform

    val assessmentType: AssessmentType

    val url: Option[String]

    val operation: State
  }

  object AbstractAssessmentFormData {
    val invigilatorsFieldMapping: Mapping[Option[Set[Usercode]]] = set(text)
      .transform[Set[Usercode]](codes => codes.filter(_.nonEmpty).map(Usercode), _.map(_.string))
      .transform[Option[Set[Usercode]]](Option.apply, _.get)

    val durationFieldMapping: Mapping[Long] = longNumber.verifying(Seq(120, 180).contains(_))

    val departmentCodeFieldMapping: Mapping[DepartmentCode] = nonEmptyText.transform(DepartmentCode(_), (u: DepartmentCode) => u.string)
  }


  case class AssessmentFormData(
    moduleCode: String,
    paperCode: String,
    section: Option[String],
    departmentCode: DepartmentCode,
    sequence: String,
    invigilators: Option[Set[Usercode]],
    title: String,
    description: Option[String],
    durationMinutes: Long,
    platform: Platform,
    assessmentType: AssessmentType,
    url: Option[String],
    operation: State
  ) extends AbstractAssessmentFormData

  val form: Form[AssessmentFormData] = Form(mapping(
    "moduleCode" -> nonEmptyText,
    "paperCode" -> nonEmptyText,
    "section" -> optional(text),
    "departmentCode" -> departmentCodeFieldMapping,
    "sequence" -> nonEmptyText,
    "invigilators" -> invigilatorsFieldMapping,
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> durationFieldMapping,
    "platform" -> Platform.formField,
    "assessmentType" -> AssessmentType.formField,
    "url" -> optional(nonEmptyText),
    "operation" -> State.formField
  )(AssessmentFormData.apply)(AssessmentFormData.unapply)
    .verifying("error.assessment.url-not-specified", data => data.operation == State.Draft || data.platform == Platform.OnlineExams || data.url.exists(_.nonEmpty))
  )

  case class AdHocAssessmentFormData(
    moduleCode: String,
    paperCode: String,
    section: Option[String],
    departmentCode: DepartmentCode,
    sequence: String,
    override val startTime: Option[LocalDateTime],
    invigilators: Option[Set[Usercode]],
    title: String,
    description: Option[String],
    durationMinutes: Long,
    platform: Platform,
    assessmentType: AssessmentType,
    url: Option[String],
    operation: State
  ) extends AbstractAssessmentFormData

  val adHocAssessmentForm: Form[AdHocAssessmentFormData] = Form(mapping(
    "moduleCode" -> nonEmptyText,
    "paperCode" -> nonEmptyText,
    "section" -> optional(text),
    "departmentCode" -> departmentCodeFieldMapping,
    "sequence" -> nonEmptyText,
    "startTime" -> nonEmptyText
      .transform[LocalDateTime](LocalDateTime.parse(_), _.toString)
      .transform[Option[LocalDateTime]](Option.apply, _.get),
    "invigilators" -> invigilatorsFieldMapping,
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> durationFieldMapping,
    "platform" -> Platform.formField,
    "assessmentType" -> AssessmentType.formField,
    "url" -> optional(nonEmptyText),
    "operation" -> State.formField
  )(AdHocAssessmentFormData.apply)(AdHocAssessmentFormData.unapply)
    .verifying("error.assessment.url-not-specified", data => data.operation == State.Draft || data.platform == Platform.OnlineExams || data.url.exists(_.nonEmpty))
  )
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
    assessmentService.findByStates(Seq(State.Draft, State.Imported, State.Approved)).successMap { assessments =>
      Ok(views.html.admin.assessments.index(assessments))
    }
  }

  def show(id: UUID): Action[AnyContent] = RequireDepartmentAssessmentManager.async { implicit request =>
    assessmentService.get(id).successMap { assessment =>
      Ok(views.html.admin.assessments.show(assessment, form.fill(AssessmentFormData(
        moduleCode = assessment.moduleCode,
        paperCode = assessment.paperCode,
        section = assessment.section,
        departmentCode = assessment.departmentCode,
        sequence = assessment.sequence,
        invigilators = Option(assessment.invigilators),
        title = assessment.title,
        description = assessment.brief.text,
        durationMinutes = assessment.duration.toMinutes,
        platform = assessment.platform,
        assessmentType = assessment.assessmentType,
        url = assessment.brief.url,
        operation = assessment.state
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
        val files = request.body.files.map(_.ref).map(f => (f.in, f.metadata))
        if (data.operation != State.Draft && data.platform == Platform.OnlineExams && files.isEmpty) {
          Future.successful(Ok(views.html.admin.assessments.create(adHocAssessmentForm.fill(data).withGlobalError("error.assessment.files-not-provided"))))
        } else {
          import helpers.DateConversion._
          assessmentService.insert(
            Assessment(
              paperCode = data.paperCode,
              section = data.section,
              title = data.title,
              startTime = data.startTime.map(_.asOffsetDateTime),
              duration = Duration.ofMinutes(data.durationMinutes),
              platform = data.platform,
              assessmentType = data.assessmentType,
              brief = Brief(
                text = data.description,
                url = data.url,
                files = Nil,
              ),
              invigilators = data.invigilators.get,
              state = data.operation,
              tabulaAssessmentId = None,
              examProfileCode = "EXAPR20",
              moduleCode = data.moduleCode,
              departmentCode = data.departmentCode,
              sequence = data.sequence,
              id = UUID.randomUUID()
            ),
            files
          ).successMap { _ =>
            Redirect(routes.AssessmentsController.index()).flashing("success" -> Messages("flash.assessment.created", data.title))
          }
        }
      })
  }


  def update(id: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = RequireDepartmentAssessmentManager(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    assessmentService.get(id).successFlatMap { assessment =>
      form.bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.assessments.show(assessment, formWithErrors))),
        data => {
          if (assessment.state != State.Approved) {
            val files = request.body.files.map(_.ref)
            if (data.operation != State.Draft && data.platform == Platform.OnlineExams && files.isEmpty) {
              Future.successful(Ok(views.html.admin.assessments.show(assessment, form.fill(data).withGlobalError("error.assessment.files-not-provided"))))
            } else {
              assessmentService.update(assessment.copy(
                title = data.title,
                duration = Duration.ofMinutes(data.durationMinutes),
                platform = data.platform,
                assessmentType = data.assessmentType,
                brief = assessment.brief.copy(
                  text = data.description,
                  url = data.url
                ),
                state = data.operation
              ), files = files.map(f => (f.in, f.metadata))).successMap { _ =>
                Redirect(routes.AssessmentsController.index()).flashing("success" -> Messages("flash.files.uploaded", files.size))
              }
            }
          } else {
            Future.successful(MethodNotAllowed(views.html.errors.approvedAssessment()))
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
