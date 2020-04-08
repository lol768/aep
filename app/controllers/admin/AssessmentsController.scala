package controllers.admin

import java.time.{Duration, LocalDateTime}
import java.util.UUID

import akka.Done
import controllers.BaseController
import domain.Assessment.{AssessmentType, Brief, Platform, State}
import domain.tabula.SitsProfile
import domain.{Assessment, Department, DepartmentCode, StudentAssessment}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, FormError, Mapping}
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

import scala.concurrent.{Await, ExecutionContext, Future}

object AssessmentsController {

  import controllers.admin.AssessmentsController.AssessmentFormData._

  object AssessmentFormData {
    val invigilatorsFieldMapping: Mapping[Set[Usercode]] = set(text)
      .transform[Set[Usercode]](codes => codes.filter(_.nonEmpty).map(Usercode), _.map(_.string))

    val departmentCodeFieldMapping: Mapping[DepartmentCode] = nonEmptyText.transform(DepartmentCode(_), (u: DepartmentCode) => u.string)

    val startTimeFieldMapping: Mapping[Option[LocalDateTime]] =
      nonEmptyText.transform[LocalDateTime](LocalDateTime.parse, _.toString)
                  .transform[Option[LocalDateTime]](Option.apply, _.get)

    def studentsFieldMapping(implicit studentInformationService: TabulaStudentInformationService, ec: ExecutionContext, t: TimingContext): Mapping[Set[UniversityID]] =
      text.transform[Set[UniversityID]](_.linesIterator.filter(_.hasText).toSet.map(UniversityID.apply), _.map(_.string).toSeq.sorted.mkString("\n"))
          .verifying(Constraint { universityIDs: Set[UniversityID] =>
            Await.result(studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs.toSeq)), scala.concurrent.duration.Duration.Inf) // Rely on HTTP timeout
              .fold(
                errors => Invalid("error.students.error", errors.map(_.message).mkString(", ")),
                info => {
                  val missing = universityIDs.filterNot(info.contains)
                  if (missing.nonEmpty) Invalid(missing.toSeq.map(universityID => ValidationError("error.students.invalid", universityID.string)))
                  else Valid
                }
              )
          })

    // Somewhere a string of a single empty space is creeping in...
    val platformsMapping: Mapping[Set[Platform]] =
      set(text).verifying ("error.assessment.platformNumber", !_.forall(p => Platform.namesToValuesMap.get(p).nonEmpty))
        .transform[Set[Platform]](_.map(Platform.withName), _.map(_.entryName))

  }

  case class AssessmentFormData(
    moduleCode: String,
    paperCode: String,
    section: Option[String],
    departmentCode: DepartmentCode,
    sequence: String,
    startTime: Option[LocalDateTime],
    students: Set[UniversityID],
    title: String,
    platform: Set[Platform],
    assessmentType: AssessmentType,
    durationMinutes: Option[Long],
    url: Option[String],
    description: Option[String],
    invigilators: Set[Usercode],
  )

  val durationConstraint: Constraint[AssessmentFormData] = Constraint { assessmentForm =>
    val validDuration = assessmentForm.durationMinutes
      .map(d => assessmentForm.assessmentType.validDurations.contains(d))
      .getOrElse(assessmentForm.assessmentType.validDurations.isEmpty)
    if(validDuration)
      Valid
    else
      Invalid(Seq(ValidationError("error.assessment.duration-not-valid", assessmentForm.assessmentType.label)))
  }

  val platformConstraint: Constraint[AssessmentFormData] = Constraint { assessmentForm =>
    if (assessmentForm.platform.isEmpty || assessmentForm.platform.size > 2) {
      Invalid(Seq(ValidationError("error.assessment.platformNumber")))
    } else {
      Valid
    }
  }

  def formMapping(existing: Option[Assessment], ready: Boolean = false)(implicit studentInformationService: TabulaStudentInformationService, ec: ExecutionContext, t: TimingContext): Form[AssessmentFormData] = {
    val baseMapping = mapping(
      "moduleCode" -> nonEmptyText,
      "paperCode" -> nonEmptyText,
      "section" -> optional(text),
      "departmentCode" -> departmentCodeFieldMapping,
      "sequence" -> nonEmptyText,
      "startTime" -> startTimeFieldMapping,
      "students" -> studentsFieldMapping,
      "title" -> nonEmptyText,
      "platform" -> platformsMapping,
      "assessmentType" -> AssessmentType.formField,
      "durationMinutes" -> optional(longNumber),
      "url" -> optional(text),
      "description" -> optional(text),
      "invigilators" -> invigilatorsFieldMapping,
    )(AssessmentFormData.apply)(AssessmentFormData.unapply)

    Form(
      if (ready) baseMapping
        .verifying("error.assessment.url-not-specified", data => data.platform == Set(Platform.OnlineExams) || data.url.exists(_.nonEmpty))
        .verifying(durationConstraint)
        .verifying(platformConstraint)
      else baseMapping
    )
  }
}

@Singleton
class AssessmentsController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  departmentService: TabulaDepartmentService,
)(implicit
  studentInformationService: TabulaStudentInformationService,
  ec: ExecutionContext
) extends BaseController {

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

  def createForm(): Action[AnyContent] = GeneralDepartmentAdminAction.async { implicit request =>
    departments().successMap { departments =>
      Ok(views.html.admin.assessments.create(formMapping(existing = None), departments))
    }
  }

  def create(): Action[MultipartFormData[TemporaryUploadedFile]] = GeneralDepartmentAdminAction(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    formMapping(existing = None).bindFromRequest().fold(
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

          val (newState, readyErrors) = formMapping(existing = None, ready = true).bindFromRequest().fold(
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
              duration = data.durationMinutes.map(Duration.ofMinutes),
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

  def updateForm(id: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(id).async { implicit request =>
    val assessment = request.assessment
    studentAssessmentService.byAssessmentId(assessment.id).successFlatMap { studentAssessments =>
      showForm(assessment, formMapping(existing = Some(assessment)).fill(AssessmentFormData(
        moduleCode = assessment.moduleCode,
        paperCode = assessment.paperCode,
        section = assessment.section,
        departmentCode = assessment.departmentCode,
        sequence = assessment.sequence,
        startTime = assessment.startTime.map(_.toLocalDateTime),
        students = studentAssessments.map(_.studentId).toSet,
        title = assessment.title,
        platform = assessment.platform,
        assessmentType = assessment.assessmentType,
        durationMinutes = assessment.duration.map(_.toMinutes),
        url = assessment.brief.url,
        description = assessment.brief.text,
        invigilators = assessment.invigilators,
      )))
    }
  }

  def update(id: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = AssessmentDepartmentAdminAction(id)(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    val assessment = request.assessment
    formMapping(existing = Some(assessment)).bindFromRequest().fold(
      formWithErrors => showForm(assessment, formWithErrors),
      data => {
        val files = request.body.files.map(_.ref)
        val fileErrors: Seq[FormError] =
          if (assessment.brief.files.isEmpty && files.isEmpty) Seq(FormError("", "error.assessment.files-not-provided"))
          else Nil

        val (newState, readyErrors) = formMapping(existing = Some(assessment), ready = true).bindFromRequest().fold(
          formWithErrors => (State.Draft, formWithErrors.errors ++ fileErrors),
          _ => if (fileErrors.isEmpty) (State.Approved, Nil) else (State.Draft, fileErrors)
        )

        val updatedIfAdHoc =
          if (assessment.tabulaAssessmentId.isEmpty) {
            import helpers.DateConversion._
            assessment.copy(
              moduleCode = data.moduleCode,
              paperCode = data.paperCode,
              section = data.section,
              departmentCode = data.departmentCode,
              sequence = data.sequence,
              startTime = data.startTime.map(_.asOffsetDateTime),
            )
          } else assessment

        val updateStudents: Future[ServiceResult[Done]] =
          if (assessment.tabulaAssessmentId.isEmpty) {
            studentAssessmentService.byAssessmentId(assessment.id).successFlatMapTo { studentAssessments =>
              val deletions: Seq[StudentAssessment] =
                studentAssessments.filterNot(sa => data.students.contains(sa.studentId))

              val additions: Seq[StudentAssessment] =
                data.students.filterNot(s => studentAssessments.exists(_.studentId == s))
                  .toSeq
                  .map { universityID =>
                    StudentAssessment(
                      id = UUID.randomUUID(),
                      assessmentId = assessment.id,
                      studentId = universityID,
                      inSeat = false,
                      startTime = None,
                      extraTimeAdjustment = None,
                      finaliseTime = None,
                      uploadedFiles = Nil,
                    )
                  }

              ServiceResults.futureSequence(
                deletions.map(studentAssessmentService.delete) ++
                additions.map(studentAssessmentService.upsert)
              ).successMapTo(_ => Done)
            }
          } else Future.successful(ServiceResults.success(Done))

        ServiceResults.zip(
          updateStudents,
          assessmentService.update(updatedIfAdHoc.copy(
            title = data.title,
            duration = data.durationMinutes.map(Duration.ofMinutes),
            platform = data.platform,
            assessmentType = data.assessmentType,
            invigilators = data.invigilators,
            brief = assessment.brief.copy(
              text = data.description,
              url = data.url
            ),
            state = newState
          ), files = files.map(f => (f.in, f.metadata)))
        ).successMap { _ =>
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
