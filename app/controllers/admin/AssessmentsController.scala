package controllers.admin

import java.time.{Duration, LocalDateTime}
import java.util.UUID

import controllers.BaseController
import domain.Assessment.{AssessmentType, Brief, Platform, State}
import domain.tabula.SitsProfile
import domain.{Assessment, DepartmentCode}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.refiners.DepartmentAdminRequest
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import services.{AssessmentService, SecurityService, StudentAssessmentService}
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.sso.{AuthenticatedRequest, UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object AssessmentsController {

  trait AbstractAssessmentFormData {
    val moduleCode: String

    val paperCode: String

    val section: Option[String]

    val departmentCode: DepartmentCode

    val sequence: String

    val startTime: Option[LocalDateTime] = None

    val invigilators: Option[Set[Usercode]] = None

    val title: String

    val description: Option[String]

    val durationMinutes: Long

    val platform: Platform

    val assessmentType: AssessmentType

    val url: Option[String]

    val operation: State
  }

  case class AssessmentFormData(
    moduleCode: String,
    paperCode: String,
    section: Option[String],
    departmentCode: DepartmentCode,
    sequence: String,
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
    "departmentCode" -> nonEmptyText.transform(DepartmentCode(_), (u: DepartmentCode) => u.string),
    "sequence" -> nonEmptyText,
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> longNumber.verifying(Seq(120, 180).contains(_)),
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
    override val invigilators: Option[Set[Usercode]],
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
    "departmentCode" -> nonEmptyText.transform(DepartmentCode(_), (u: DepartmentCode) => u.string),
    "sequence" -> nonEmptyText,
    "startTime" -> nonEmptyText
      .transform[LocalDateTime](LocalDateTime.parse(_), _.toString)
      .transform[Option[LocalDateTime]](Option.apply, _.get),
    "invigilators" -> set(text)
      .transform[Set[Usercode]](codes => codes.filter(_.nonEmpty).map(Usercode), _.map(_.string))
      .transform[Option[Set[Usercode]]](Option.apply, _.get),
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> longNumber.verifying(Seq(120, 180).contains(_)),
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
  studentAssessmentService: StudentAssessmentService,
  studentInformationService: TabulaStudentInformationService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit ec: ExecutionContext) extends BaseController {

  import AssessmentsController._
  import security._

  def index: Action[AnyContent] = GeneralDepartmentAdminAction.async { implicit request =>
    assessmentService.findByStates(Seq(State.Draft, State.Imported, State.Approved)).successMap { assessments =>
      Ok(views.html.admin.assessments.index(filterForDeptAdmin(filterForDeptAdmin(assessments))))
    }
  }

  private def showForm(assessment: Assessment, assessmentForm: Form[AssessmentFormData])(implicit request: AuthenticatedRequest[_]): Future[Result] =
    studentAssessmentService.byAssessmentId(assessment.id).successFlatMap { studentAssessments =>
      studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = studentAssessments.map(_.studentId)))
        .map(_.fold(_ => Map.empty[UniversityID, SitsProfile], identity))
        .map { studentInformation =>
          Ok(views.html.admin.assessments.show(assessment, studentAssessments, studentInformation, assessmentForm))
        }
    }

  def show(id: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(id).async { implicit request =>
    val assessment = request.assessment
    showForm(assessment, form.fill(AssessmentFormData(
      moduleCode = assessment.moduleCode,
      paperCode = assessment.paperCode,
      section = assessment.section,
      departmentCode = assessment.departmentCode,
      sequence = assessment.sequence,
      title = assessment.title,
      description = assessment.brief.text,
      durationMinutes = assessment.duration.toMinutes,
      platform = assessment.platform,
      assessmentType = assessment.assessmentType,
      url = assessment.brief.url,
      operation = assessment.state
    )))
  }

  def create(): Action[AnyContent] = GeneralDepartmentAdminAction { implicit request =>
    Ok(views.html.admin.assessments.create(adHocAssessmentForm))
  }

  def save(): Action[MultipartFormData[TemporaryUploadedFile]] = GeneralDepartmentAdminAction(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    adHocAssessmentForm.bindFromRequest().fold(
      formWithErrors => Future.successful(Ok(views.html.admin.assessments.create(formWithErrors))),
      data => {
        val files = request.body.files.map(_.ref).map(f => (f.in, f.metadata))
        if (request.departments.exists(_.code.toLowerCase == data.departmentCode.lowerCase)) {
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
              files = request.body.files.map(_.ref).map(f => (f.in, f.metadata)),
            ).successMap { _ =>
              Redirect(routes.AssessmentsController.index()).flashing("success" -> Messages("flash.assessment.created", data.title))
            }
          }
        } else { // User is not an admin for the supplied department
          Future.successful(Redirect(
            controllers.admin.routes.AssessmentsController.create()
          ).flashing("error" -> Messages("error.permissions.notDepartmentAdminForSelected", data.departmentCode)))
        }
      })
  }

  def update(id: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = AssessmentDepartmentAdminAction(id)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    val assessment = request.assessment
    form.bindFromRequest().fold(
      formWithErrors => showForm(assessment, formWithErrors),
      data => {
        if (assessment.state != State.Approved) {
          val files = request.body.files.map(_.ref)
          if (data.operation != State.Draft && data.platform == Platform.OnlineExams && files.isEmpty) {
            showForm(assessment, form.fill(data).withGlobalError("error.assessment.files-not-provided"))
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

  def getFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = RequireDepartmentAssessmentManager.async { implicit request =>
    assessmentService.get(assessmentId).successFlatMap { assessment =>
      assessment.brief.files.find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound("File not found")))
    }
  }

  private def deptAdminCanView(assessment: Assessment)(
    implicit request: DepartmentAdminRequest[AnyContent]
  ): Boolean =
    request.departments
      .exists(_.code.toLowerCase == assessment.departmentCode.lowerCase)

  private def filterForDeptAdmin(assessments: Seq[Assessment])(
    implicit request: DepartmentAdminRequest[AnyContent]
  ): Seq[Assessment] =
    assessments.filter(deptAdminCanView)

}
