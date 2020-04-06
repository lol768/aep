package controllers.admin

import java.time.{Duration, LocalDateTime}
import java.util.UUID

import controllers.BaseController
import domain.Assessment.{AssessmentType, Brief, Platform, State}
import domain.tabula.SitsProfile
import domain.{Assessment, Department, DepartmentCode}
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, FormError, Mapping}
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.refiners.DepartmentAdminRequest
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import services.tabula.{TabulaDepartmentService, TabulaStudentInformationService}
import services.{AssessmentService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.sso.{AuthenticatedRequest, UniversityID, Usercode}

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

    val invigilators: Set[Usercode]

    val title: String

    val description: Option[String]

    val durationMinutes: Long

    val platform: Seq[Platform]

    val assessmentType: AssessmentType

    val url: Option[String]
  }

  object AbstractAssessmentFormData {
    val invigilatorsFieldMapping: Mapping[Set[Usercode]] = set(text)
      .transform[Set[Usercode]](codes => codes.filter(_.nonEmpty).map(Usercode), _.map(_.string))

    val durationFieldMapping: Mapping[Long] = longNumber.verifying(Seq(120, 180).contains(_))

    val departmentCodeFieldMapping: Mapping[DepartmentCode] = nonEmptyText.transform(DepartmentCode(_), (u: DepartmentCode) => u.string)
  }


  case class AssessmentFormData(
    moduleCode: String,
    paperCode: String,
    section: Option[String],
    departmentCode: DepartmentCode,
    sequence: String,
    invigilators: Set[Usercode],
    title: String,
    description: Option[String],
    durationMinutes: Long,
    platform: List[Platform],
    assessmentType: AssessmentType,
    url: Option[String],
  ) extends AbstractAssessmentFormData

  val formMapping: Mapping[AssessmentFormData] = mapping(
    "moduleCode" -> nonEmptyText,
    "paperCode" -> nonEmptyText,
    "section" -> optional(text),
    "departmentCode" -> departmentCodeFieldMapping,
    "sequence" -> nonEmptyText,
    "invigilators" -> invigilatorsFieldMapping,
    "title" -> nonEmptyText,
    "description" -> optional(nonEmptyText),
    "durationMinutes" -> durationFieldMapping,
    "platform" -> list(Platform.formField),
    "assessmentType" -> AssessmentType.formField,
    "url" -> optional(text),
  )(AssessmentFormData.apply)(AssessmentFormData.unapply)

  val form: Form[AssessmentFormData] = Form(formMapping)
  val readyForm: Form[AssessmentFormData] = Form(readyMapping(formMapping))

  case class AdHocAssessmentFormData(
    moduleCode: String,
    paperCode: String,
    section: Option[String],
    departmentCode: DepartmentCode,
    sequence: String,
    override val startTime: Option[LocalDateTime],
    invigilators: Set[Usercode],
    title: String,
    description: Option[String],
    durationMinutes: Long,
    platform: List[Platform],
    assessmentType: AssessmentType,
    url: Option[String],
  ) extends AbstractAssessmentFormData

  val adHocAssessmentFormMapping: Mapping[AdHocAssessmentFormData] = mapping(
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
    "platform" -> list(Platform.formField),
    "assessmentType" -> AssessmentType.formField,
    "url" -> optional(text),
  )(AdHocAssessmentFormData.apply)(AdHocAssessmentFormData.unapply)

  val adHocAssessmentForm: Form[AdHocAssessmentFormData] = Form(adHocAssessmentFormMapping)
  val adHocAssessmentReadyForm: Form[AdHocAssessmentFormData] = Form(readyMapping(adHocAssessmentFormMapping))

  // Additional validation on the mapping that allows the state to go to Ready if it passes
  def readyMapping[A <: AbstractAssessmentFormData](mapping: Mapping[A]): Mapping[A] =
    mapping
      .verifying("error.assessment.url-not-specified", data => data.platform == Platform.OnlineExams || data.url.exists(_.nonEmpty))
}

@Singleton
class AssessmentsController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  studentInformationService: TabulaStudentInformationService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  departmentService: TabulaDepartmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  import AssessmentsController._
  import security._

  def index: Action[AnyContent] = GeneralDepartmentAdminAction.async { implicit request =>
    assessmentService.findByStates(Seq(State.Draft, State.Imported, State.Approved)).successMap { assessments =>
      Ok(views.html.admin.assessments.index(filterForDeptAdmin(filterForDeptAdmin(assessments))))
    }
  }

  private def showForm(assessment: Assessment, assessmentForm: Form[AssessmentFormData])(implicit request: AuthenticatedRequest[_]): Future[Result] =
    ServiceResults.zip(
      studentAssessmentService.byAssessmentId(assessment.id),
      departments()
    ).successFlatMap { case (studentAssessments, departments) =>
      studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = studentAssessments.map(_.studentId)))
        .map(_.fold(_ => Map.empty[UniversityID, SitsProfile], identity))
        .map { studentInformation =>
          Ok(views.html.admin.assessments.show(assessment, studentAssessments, studentInformation, assessmentForm, departments))
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
      invigilators = assessment.invigilators,
      title = assessment.title,
      description = assessment.brief.text,
      durationMinutes = assessment.duration.toMinutes,
      platform = assessment.platform,
      assessmentType = assessment.assessmentType,
      url = assessment.brief.url,
    )))
  }

  def create(): Action[AnyContent] = GeneralDepartmentAdminAction.async { implicit request =>
    departments().successMap { departments =>
      Ok(views.html.admin.assessments.create(adHocAssessmentForm, departments))
    }
  }

  def save(): Action[MultipartFormData[TemporaryUploadedFile]] = GeneralDepartmentAdminAction(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    adHocAssessmentForm.bindFromRequest().fold(
      formWithErrors => {
        departments().successMap { departments =>
          Ok(views.html.admin.assessments.create(formWithErrors, departments))
        }
      },
      data => {
        val files = request.body.files.map(_.ref).map(f => (f.in, f.metadata))
        if (request.departments.exists(_.code.toLowerCase == data.departmentCode.lowerCase)) {
          val fileErrors: Seq[FormError] =
            if (files.isEmpty) Seq(FormError("", "error.assessment.files-not-provided"))
            else Nil

          val (newState, readyErrors) = adHocAssessmentReadyForm.bindFromRequest().fold(
            formWithErrors => (State.Draft, formWithErrors.errors ++ fileErrors),
            _ => if (fileErrors.isEmpty) (State.Approved, Nil) else (State.Draft, fileErrors)
          )

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
              invigilators = data.invigilators,
              state = newState,
              tabulaAssessmentId = None,
              examProfileCode = "EXAPR20",
              moduleCode = data.moduleCode,
              departmentCode = data.departmentCode,
              sequence = data.sequence,
              id = UUID.randomUUID()
            ),
            files = request.body.files.map(_.ref).map(f => (f.in, f.metadata)),
          ).successMap { _ =>
            Redirect(routes.AssessmentsController.index()).flashing {
              if (newState == State.Approved)
                "success" -> Messages("flash.assessment.created", data.title)
              else
                "warning" -> Messages("flash.assessment.created.notReady", data.title, readyErrors.flatMap(e => e.messages.map(m => Messages(m, e.args))).mkString("; "))
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
        val files = request.body.files.map(_.ref)
        val fileErrors: Seq[FormError] =
          if (assessment.brief.files.isEmpty && files.isEmpty) Seq(FormError("", "error.assessment.files-not-provided"))
          else Nil

        val (newState, readyErrors) = readyForm.bindFromRequest().fold(
          formWithErrors => (State.Draft, formWithErrors.errors ++ fileErrors),
          _ => if (fileErrors.isEmpty) (State.Approved, Nil) else (State.Draft, fileErrors)
        )

        assessmentService.update(assessment.copy(
          title = data.title,
          duration = Duration.ofMinutes(data.durationMinutes),
          platform = data.platform,
          assessmentType = data.assessmentType,
          invigilators = data.invigilators,
          brief = assessment.brief.copy(
            text = data.description,
            url = data.url
          ),
          state = newState
        ), files = files.map(f => (f.in, f.metadata))).successMap { _ =>
          Redirect(routes.AssessmentsController.index()).flashing {
            if (newState == State.Approved)
              "success" -> Messages("flash.assessment.updated", data.title)
            else
              "warning" -> Messages("flash.assessment.updated.notReady", data.title, readyErrors.flatMap(e => e.messages.map(m => Messages(m, e.args))).mkString("; "))
          }
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

  private def departments()(implicit timingContext: TimingContext): Future[ServiceResult[Seq[Department]]] = {
    departmentService.getDepartments().successMapTo { departments =>
      departments.sortBy(_.name).map { tabulaDepartment =>
        Department(code = DepartmentCode(tabulaDepartment.code), name = tabulaDepartment.name)
      }
    }
  }

}
