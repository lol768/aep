package controllers.admin

import java.time.{Duration, LocalDateTime}
import java.util.UUID

import akka.Done
import controllers.invigilation.InvigilatorAssessmentController.lookupInvigilatorUsers
import controllers.{BaseController, FormMappings}
import domain.Assessment.{Brief, DurationStyle, Platform, State}
import domain.tabula.SitsProfile
import domain.{Assessment, Department, DepartmentCode, StudentAssessment}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData, Result}
import services.refiners.DepartmentAdminRequest
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import services.tabula.{TabulaAssessmentService, TabulaAssignmentService, TabulaDepartmentService, TabulaStudentInformationService}
import services.{AssessmentClientNetworkActivityService, AssessmentService, SecurityService, StudentAssessmentService}
import system.Features
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.{TemporaryUploadedFile, UploadedFileConfiguration}
import warwick.sso.{AuthenticatedRequest, UniversityID, UserLookupService, Usercode}

import scala.concurrent.{Await, ExecutionContext, Future}

object AdminAssessmentsController {

  import controllers.admin.AdminAssessmentsController.AssessmentFormData._

  object AssessmentFormData {
    val invigilatorsFieldMapping: Mapping[Set[Usercode]] = set(text)
      .transform[Set[Usercode]](codes => codes.filter(_.nonEmpty).map(Usercode), _.map(_.string))

    val departmentCodeFieldMapping: Mapping[DepartmentCode] = nonEmptyText.transform(DepartmentCode(_), (u: DepartmentCode) => u.string)

    val startTimeFieldMapping: Mapping[Option[LocalDateTime]] =
      nonEmptyText.transform[LocalDateTime](LocalDateTime.parse, _.toString)
                  .transform[Option[LocalDateTime]](Option.apply, _.get)

    def studentsFieldMapping(implicit studentInformationService: TabulaStudentInformationService, ec: ExecutionContext, t: TimingContext): Mapping[Set[UniversityID]] =
      text.transform[Set[UniversityID]](_.split("\\s+").filter(_.hasText).toSet.map(UniversityID.apply), _.map(_.string).toSeq.sorted.mkString("\n"))
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
      set(text).verifying ("error.assessment.platformNumber", theSet => theSet.forall(p => Platform.namesToValuesMap.contains(p)))
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
    durationMinutes: Option[Long],
    durationStyle: Option[DurationStyle],
    urls: Map[Platform, String],
    description: Option[String],
    invigilators: Set[Usercode],
  )

  val durationConstraint: Constraint[AssessmentFormData] = Constraint { assessmentForm =>
    assessmentForm.durationStyle.map { at =>
      val validDuration = assessmentForm.durationMinutes
          .map(d => at.validDurations.contains(d))
          .getOrElse(at.validDurations.isEmpty)
      if (validDuration) Valid
        else Invalid(Seq(ValidationError("error.assessment.duration-not-valid", at.label)))
    }.getOrElse(Valid) // if duration style isn't defined don't validate on duration
  }

  val platformConstraint: Constraint[AssessmentFormData] = Constraint { assessmentForm =>
    if (assessmentForm.platform.isEmpty || assessmentForm.platform.size > 2) {
      Invalid(Seq(ValidationError("error.assessment.platformNumber")))
    } else {
      Valid
    }
  }

  val urlConstraint: Constraint[AssessmentFormData] = Constraint { assessmentForm =>
    val missingUrls = assessmentForm.platform.filter(_.requiresUrl).filter(platform =>
      assessmentForm.urls.get(platform).forall(!_.hasText)
    )
    if (missingUrls.isEmpty)
      Valid
    else
      Invalid(Seq(ValidationError("error.assessment.url-not-specified", missingUrls.map(_.label).mkString(", "))))
  }

  def notStarted(existing: Option[Assessment]): Constraint[AssessmentFormData] = Constraint { _ =>
    if (existing.forall(_.tabulaAssessmentId.isEmpty))
      Valid
    else if (existing.exists(_.hasStartTimePassed()))
      Invalid(Seq(ValidationError("error.assessment.started")))
    else
      Valid
  }

  def formMapping(existing: Option[Assessment], ready: Boolean = false)(implicit studentInformationService: TabulaStudentInformationService, ec: ExecutionContext, t: TimingContext): Form[AssessmentFormData] = {
    val baseMapping = mapping(
      "moduleCode" -> nonEmptyText,
      "paperCode" -> nonEmptyText,
      "section" -> optional(text),
      "departmentCode" -> departmentCodeFieldMapping,
      "sequence" -> nonEmptyText,
      "startTime" -> startTimeFieldMapping,
      "students" -> existing.filter(_.tabulaAssessmentId.nonEmpty).map(_ => ignored(Set.empty[UniversityID])).getOrElse(studentsFieldMapping),
      "title" -> nonEmptyText,
      "platform" -> platformsMapping,
      "durationMinutes" -> optional(longNumber),
      "durationStyle" -> optional(DurationStyle.formField),
      "urls" -> mapping[Map[Platform, String], Option[String], Option[String], Option[String], Option[String], Option[String]](
        Platform.OnlineExams.entryName -> optional(text),
        Platform.Moodle.entryName -> optional(text),
        Platform.QuestionmarkPerception.entryName -> optional(text),
        Platform.TabulaAssignment.entryName -> optional(text),
        Platform.MyWBS.entryName -> optional(text),
      )(
        (onlineExamsUrl, moodleUrl, qmpUrl, tabulaUrl, myWBSUrl) => Map(
          Platform.OnlineExams -> onlineExamsUrl,
          Platform.Moodle -> moodleUrl,
          Platform.QuestionmarkPerception -> qmpUrl,
          Platform.TabulaAssignment -> tabulaUrl,
          Platform.MyWBS -> myWBSUrl
        ).filter { case (_, url) => url.exists(_.nonEmpty)}.map { case (k, v) => k -> v.get }
      )(urls => Some((
        urls.get(Platform.OnlineExams),
        urls.get(Platform.Moodle),
        urls.get(Platform.QuestionmarkPerception),
        urls.get(Platform.TabulaAssignment),
        urls.get(Platform.MyWBS)
      ))),
      "description" -> optional(text),
      "invigilators" -> invigilatorsFieldMapping,
    )(AssessmentFormData.apply)(AssessmentFormData.unapply)
      .verifying(notStarted(existing))

    Form(
      if (ready) baseMapping
        .verifying("error.assessment.duration-style-not-specified", data => data.durationStyle.isDefined)
        .verifying(urlConstraint)
        .verifying(durationConstraint)
        .verifying(platformConstraint)
        .verifying("error.assessment.description.required", _.description.exists(_.hasText))
        .verifying("error.assessment.invigilators.required", _.invigilators.nonEmpty)
        .verifying("error.assessment.students.required", data => existing.exists(_.tabulaAssessmentId.nonEmpty) || data.students.nonEmpty)
      else baseMapping
    )
  }
}

@Singleton
class AdminAssessmentsController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  departmentService: TabulaDepartmentService,
  userLookup: UserLookupService,
  tabulaAssessmentService: TabulaAssessmentService,
  tabulaAssignmentService: TabulaAssignmentService,
  networkActivityService: AssessmentClientNetworkActivityService,
  configuration: Configuration,
  features: Features,
)(implicit
  studentInformationService: TabulaStudentInformationService,
  ec: ExecutionContext
) extends BaseController {

  import AdminAssessmentsController._
  import security._

  private[this] lazy val uploadedFileConfig = UploadedFileConfiguration.fromConfiguration(configuration)

  def index: Action[AnyContent] = GeneralDepartmentAdminAction.async { implicit request =>
    assessmentService.findByStates(Seq(State.Draft, State.Imported, State.Approved)).successMap { assessments =>
      Ok(views.html.admin.assessments.index(filterForDeptAdmin(assessments)))
    }
  }

  // If it's ad-hoc, and it's either before the start date or none of the students have started, you can delete it
  private def canBeDeleted(assessment: Assessment, studentAssessments: Seq[StudentAssessment]): Boolean =
    assessment.tabulaAssessmentId.isEmpty && (
      assessment.startTime.exists(_.isAfter(JavaTime.offsetDateTime)) || studentAssessments.forall(_.startTime.isEmpty)
    )

  private def showForm(assessment: Assessment, assessmentForm: Form[AssessmentFormData])(implicit request: AuthenticatedRequest[_]): Future[Result] =
    ServiceResults.zip(
      studentAssessmentService.byAssessmentId(assessment.id),
      departments()
    ).successFlatMap { case (studentAssessments, departments) =>
      studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = studentAssessments.map(_.studentId)))
        .map(_.fold(_ => Map.empty[UniversityID, SitsProfile], identity))
        .map { studentInformation =>
          Ok(views.html.admin.assessments.form(assessment, studentAssessments, studentInformation, assessmentForm, departments, canBeDeleted(assessment, studentAssessments)))
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
              durationStyle = data.durationStyle,
              brief = Brief(
                text = data.description,
                urls = data.urls,
                files = Nil,
              ),
              invigilators = data.invigilators,
              state = newState,
              tabulaAssessmentId = None,
              tabulaAssignments = Set(),
              examProfileCode = "EXAPR20",
              moduleCode = data.moduleCode,
              departmentCode = data.departmentCode,
              sequence = data.sequence,
              id = UUID.randomUUID()
            ),
            files = request.body.files.map(_.ref).map(f => (f.in, f.metadata)),
          ).successFlatMapTo(newAssessment =>
            studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(data.students.toSeq)).successFlatMapTo { studentInfos =>
              studentAssessmentService.insert(data.students.map(universityID =>
                StudentAssessment(
                  id = UUID.randomUUID(),
                  assessmentId = newAssessment.id,
                  occurrence = None,
                  academicYear = None, // See comment in TabulaAssessmentService.generateAssignments
                  studentId = universityID,
                  inSeat = false,
                  startTime = None,
                  extraTimeAdjustmentPerHour = if (features.importStudentExtraTime) studentInfos.get(universityID).flatMap(_.totalExtraTimePerHour) else None,
                  explicitFinaliseTime = None,
                  uploadedFiles = Nil,
                  tabulaSubmissionId = None
                )
              ))
            }
          ).successMap(_ =>
            Redirect(routes.AdminAssessmentsController.index()).flashing {
              if (newState == State.Approved)
                "success" -> Messages("flash.assessment.created", data.title)
              else
                "warning" -> Messages("flash.assessment.created.notReady", data.title, readyErrors.flatMap(e => e.messages.map(m => Messages(m, e.args:_*))).mkString("; "))
            }
          )
        } else { // User is not an admin for the supplied department
          Future.successful(Redirect(
            controllers.admin.routes.AdminAssessmentsController.create()
          ).flashing("error" -> Messages("error.permissions.notDepartmentAdminForSelected", data.departmentCode)))
        }
      }).map(uploadedFileControllerHelper.cleanupTemporaryFiles(_))
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
        durationMinutes = assessment.duration.map(_.toMinutes),
        durationStyle = assessment.durationStyle,
        urls = assessment.brief.urls,
        description = assessment.brief.text,
        invigilators = assessment.invigilators,
      )))
    }
  }

  def view(id: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(id).async { implicit request =>
    val assessment = request.assessment
    ServiceResults.zip(
      studentAssessmentService.byAssessmentId(assessment.id),
      departmentService.getDepartments(),
      tabulaAssignmentService.getByAssessment(assessment),
      networkActivityService.getLatestInvigilatorActivityFor(assessment.id),
    ).successFlatMap { case (studentAssessments, departments, tabulaAssignments, invigilatorActivity) =>
      studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = studentAssessments.map(_.studentId)))
        .map(_.fold(_ => Map.empty[UniversityID, SitsProfile], identity))
        .map { studentInformation =>
          Ok(views.html.admin.assessments.view(
            assessment,
            tabulaAssignments,
            studentAssessments,
            studentInformation,
            departments.find(_.code == assessment.departmentCode.string),
            lookupInvigilatorUsers(assessment)(userLookup),
            invigilatorActivity,
          ))
        }
    }
  }

  def studentPreview(id: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(id) { implicit request =>
    Ok(views.html.admin.assessments.studentPreview(request.assessment, uploadedFileConfig))
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
            // Only update fields here that are appropriate for mock assessments (other fields may be updated below)
            assessment.copy(
              moduleCode = data.moduleCode,
              paperCode = data.paperCode,
              section = data.section,
              departmentCode = data.departmentCode,
              sequence = data.sequence,
              startTime = data.startTime.map(_.asOffsetDateTime),
              durationStyle = data.durationStyle,
            )
          } else assessment

        val updateStudents: Future[ServiceResult[Done]] =
          if (assessment.tabulaAssessmentId.isEmpty) {
            studentAssessmentService.byAssessmentId(assessment.id).successFlatMapTo { studentAssessments =>
              studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(data.students.toSeq)).successFlatMapTo { studentInfos =>
                  val deletions: Seq[StudentAssessment] =
                    studentAssessments.filterNot(sa => data.students.contains(sa.studentId))

                  val additions: Seq[StudentAssessment] =
                    data.students.filterNot(s => studentAssessments.exists(_.studentId == s))
                      .toSeq
                      .map { universityID =>
                        StudentAssessment(
                          id = UUID.randomUUID(),
                          assessmentId = assessment.id,
                          occurrence = None,
                          academicYear = None, // See comment in TabulaAssessmentService.generateAssignments
                          studentId = universityID,
                          inSeat = false,
                          startTime = None,
                          extraTimeAdjustmentPerHour = if (features.importStudentExtraTime) studentInfos.get(universityID).flatMap(_.totalExtraTimePerHour) else None,
                          explicitFinaliseTime = None,
                          uploadedFiles = Nil,
                          tabulaSubmissionId = None
                        )
                      }

                  ServiceResults.futureSequence(
                    deletions.map(studentAssessmentService.delete) ++
                      additions.map(studentAssessmentService.upsert)
                  ).successMapTo(_ => Done)
              }

            }
          } else Future.successful(ServiceResults.success(Done))

        ServiceResults.zip(
          updateStudents,
          assessmentService.update(
            // Updates that are valid whether or not this is a mock assessment
            Assessment(
              title = data.title,
              duration = data.durationMinutes.map(Duration.ofMinutes),
              platform = data.platform,
              invigilators = data.invigilators,
              state = newState,
              brief = assessment.brief.copy(
                text = data.description,
                urls = data.urls
              ),
              // Rest are unchanged (may have been changed above)
              id = updatedIfAdHoc.id,
              durationStyle = updatedIfAdHoc.durationStyle,
              paperCode = updatedIfAdHoc.paperCode,
              section = updatedIfAdHoc.section,
              startTime = updatedIfAdHoc.startTime,
              tabulaAssessmentId = updatedIfAdHoc.tabulaAssessmentId,
              tabulaAssignments = updatedIfAdHoc.tabulaAssignments,
              examProfileCode = updatedIfAdHoc.examProfileCode,
              moduleCode = updatedIfAdHoc.moduleCode,
              departmentCode = updatedIfAdHoc.departmentCode,
              sequence = updatedIfAdHoc.sequence
            ), files = files.map(f => (f.in, f.metadata)))
        ).successMap { _ =>
          Redirect(routes.AdminAssessmentsController.index()).flashing {
            if (newState == State.Approved)
              "success" -> Messages("flash.assessment.updated", data.title)
            else
              "warning" -> Messages("flash.assessment.updated.notReady", data.title, readyErrors.flatMap(e => e.messages.map(m => Messages(m, e.args:_*))).mkString("; "))
          }
        }
      }).map(uploadedFileControllerHelper.cleanupTemporaryFiles(_))
  }

  def generateAssignments(id: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(id).async { implicit request =>
    val assessment = request.assessment

    tabulaAssessmentService.generateAssignments(assessment.asAssessmentMetadata).successMap { _ =>
      Redirect(routes.AdminAssessmentsController.view(assessment.id))
        .flashing { "success" -> Messages("flash.assessment.generatedAssignments", assessment.title) }
    }
  }


  def getFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    request.assessment.brief.files.find(_.id == fileId)
      .map(uploadedFileControllerHelper.serveFile(_))
      .getOrElse(Future.successful(NotFound("File not found")))
  }

  def deleteForm(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    studentAssessmentService.byAssessmentId(request.assessment.id).successMap(studentAssessments =>
      Ok(views.html.admin.assessments.delete(FormMappings.confirmForm, canBeDeleted(request.assessment, studentAssessments)))
    )
  }

  def delete(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit request =>
    studentAssessmentService.byAssessmentId(request.assessment.id).successFlatMap(studentAssessments =>
      if (!canBeDeleted(request.assessment, studentAssessments)) {
        throw new IllegalArgumentException("Cannot delete this assessment")
      } else {
        FormMappings.confirmForm.bindFromRequest.fold(
          formWithErrors => Future.successful(Ok(views.html.admin.assessments.delete(formWithErrors, canBeDeleted = true))),
          _ => assessmentService.delete(request.assessment).successMap(_ =>
            Redirect(routes.AdminAssessmentsController.index()).flashing("success" -> Messages("flash.assessment.deleted", request.assessment.title))
          )
        )
      }
    )
  }

  def invigilatorsAjax(assessmentId: UUID): Action[AnyContent] = AssessmentDepartmentAdminAction(assessmentId).async { implicit req =>
    val assessment = req.assessment

    networkActivityService.getLatestInvigilatorActivityFor(assessmentId).successMap { result =>
      Ok(views.html.tags.invigilatorsList(assessment, lookupInvigilatorUsers(assessment)(userLookup), result, routes.AdminAssessmentsController.invigilatorsAjax(assessmentId)))
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
