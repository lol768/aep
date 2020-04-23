package controllers.sysadmin

import java.util.UUID

import controllers.BaseController
import controllers.admin.routes
import domain.DepartmentCode
import domain.tabula._
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsString, Json, Writes}
import play.api.libs.mailer.{Attachment, Email}
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services._
import services.tabula.TabulaAssessmentService.{GetAssessmentGroupMembersOptions, GetAssessmentsOptions}
import services.tabula.{TabulaAssessmentService, TabulaDepartmentService}
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import uk.ac.warwick.util.termdates.AcademicYear
import warwick.core.helpers.ServiceResults
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.sso.{GroupName, UniversityID, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object SysadminTestController {

  case class EmailFormData(to: Usercode, email: Email)

  val emailForm: Form[EmailFormData] = Form(mapping(
    "to" -> nonEmptyText.transform[Usercode](Usercode.apply, _.string),
    "email" -> mapping(
      "subject" -> nonEmptyText,
      "from" -> email,
      "to" -> ignored[Seq[String]](Nil),
      "bodyText" -> optional(nonEmptyText),
      "bodyHtml" -> ignored[Option[String]](None),
      "charset" -> ignored[Option[String]](None),
      "cc" -> ignored[Seq[String]](Nil),
      "bcc" -> ignored[Seq[String]](Nil),
      "replyTo" -> ignored[Seq[String]](Nil),
      "bounceAddress" -> ignored[Option[String]](None),
      "attachments" -> ignored[Seq[Attachment]](Nil),
      "headers" -> ignored[Seq[(String, String)]](Nil),
    )(Email.apply)(Email.unapply)
  )(EmailFormData.apply)(EmailFormData.unapply))


  case class MyWarwickFormData(
    user: Option[Usercode],
    group: Option[GroupName],
    title: String,
    url: String,
    text: String,
    activityType: String,
    alert: Boolean
  ) {
    def toActivity: Activity = new Activity(
      user.map(_.string).toSet.asJava,
      group.map(_.string).toSet.asJava,
      title,
      url,
      text,
      activityType
    )
  }

  val myWarwickForm: Form[MyWarwickFormData] = Form(mapping(
    "user" -> text.transform[Option[Usercode]](_.maybeText.map(Usercode.apply), _.map(_.string).getOrElse("")),
    "group" -> text.transform[Option[GroupName]](_.maybeText.map(GroupName.apply), _.map(_.string).getOrElse("")),
    "title" -> nonEmptyText,
    "url" -> text,
    "text" -> text,
    "activityType" -> nonEmptyText,
    "alert" -> boolean
  )(MyWarwickFormData.apply)(MyWarwickFormData.unapply))

  case class TabulaSubmissionGenaratorFormData(
    assessmentId: String,
    studentId: Option[UniversityID]
  )

  val tabulaSubmissionGenaratorForm: Form[TabulaSubmissionGenaratorFormData] = Form(mapping(
    "assessmentId" -> nonEmptyText,
    "studentId" -> text.transform[Option[UniversityID]](_.maybeText.map(UniversityID.apply), _.map(_.string).getOrElse(""))
  )(TabulaSubmissionGenaratorFormData.apply)(TabulaSubmissionGenaratorFormData.unapply))
}

@Singleton
class SysadminTestController @Inject()(
  securityService: SecurityService,
  emailService: EmailService,
  userLookupService: UserLookupService,
  myWarwickService: MyWarwickService,
  scheduler: Scheduler,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  tabulaAssessments: TabulaAssessmentService,
  tabulaDepartments: TabulaDepartmentService,
  tabulaAssessmentImportService: TabulaAssessmentImportService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import SysadminTestController._
  import securityService._

  def home: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    uploadedFileService.listWithoutOwner().successMap { files =>
      Ok(views.html.sysadmin.test(emailForm, myWarwickForm, files, uploadedFileControllerHelper.supportedMimeTypes))
    }
  }

  def uploadTest: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.uploadExample())
  }

  def receiveUpload: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok
  }

  def sendEmail: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    emailForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest),
      data => emailService.queue(data.email, Seq(userLookupService.getUser(data.to).get)).successMap { emails =>
        redirectHome.flashing("success" -> Messages("flash.emails.queued", emails.size))
      }
    )
  }

  def sendMyWarwick: Action[AnyContent] = RequireSysadmin { implicit request =>
    myWarwickForm.bindFromRequest().fold(
      _ => BadRequest,
      data => {
        if (data.alert) myWarwickService.queueNotification(data.toActivity, scheduler)
        else myWarwickService.queueActivity(data.toActivity, scheduler)

        redirectHome.flashing("success" -> Messages("flash.mywarwick.queued"))
      }
    )
  }

  def uploadFile: Action[MultipartFormData[TemporaryUploadedFile]] = RequireSysadmin(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    val files = request.body.files.map(_.ref)
    ServiceResults.futureSequence(files.map { ref => uploadedFileService.store(ref.in, ref.metadata) }).successMap { files =>
      redirectHome.flashing("success" -> Messages("flash.files.uploaded", files.size))
    }.map(uploadedFileControllerHelper.cleanupTemporaryFiles(_))
  }

  def downloadFile(id: UUID): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    uploadedFileService.get(id).successFlatMap(uploadedFileControllerHelper.serveFile)
  }

  def importAssessments(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    tabulaAssessmentImportService.importAssessments().successMap(_ => Ok("Done"))
  }

  def assessmentComponents(deptCode: DepartmentCode, examProfileCode: String): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    implicit val writeAcademicYear: Writes[AcademicYear] = ay => JsString(ay.toString)
    implicit val writeUniversityID: Writes[UniversityID] = o => JsString(o.string)
    implicit val writeExamScheduleStudent: Writes[ExamPaperScheduleStudent] = Json.writes[ExamPaperScheduleStudent]
    implicit val writeExamSchedule: Writes[ExamPaperSchedule] = Json.writes[ExamPaperSchedule]
    implicit val writeExam: Writes[ExamPaper] = Json.writes[ExamPaper]
    implicit val writesDepartmentIdentity: Writes[DepartmentIdentity] = Json.writes[DepartmentIdentity]
    implicit val writesModule: Writes[Module] = Json.writes[Module]

    implicit val writes: Writes[AssessmentComponent] = Json.writes[AssessmentComponent]

    tabulaAssessments.getAssessments(GetAssessmentsOptions(deptCode = deptCode.string, withExamPapersOnly = true, inUseOnly = false, examProfileCode = Some(examProfileCode))).successMap { r =>
      Ok(Json.toJson(r)(Writes.seq(writes)))
    }
  }

  def assessmentComponentMembers(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    implicit val writeUniversityID: Writes[UniversityID] = u => JsString(u.string)
    implicit val writes = Json.writes[ExamMembership]
    tabulaAssessments.getAssessmentGroupMembers(GetAssessmentGroupMembersOptions(deptCode = "CH", academicYear = AcademicYear.starting(2019), paperCodes = Seq("EC3120", "EC9011_A"))).successMap { r =>
      Ok(Json.toJson(r)(Writes.map(writes)))
    }
  }

  def departments(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    implicit val writeDepartmentidentity = Json.writes[DepartmentIdentity]
    implicit val writes = Json.writes[Department]
    tabulaDepartments.getDepartments().successMap { r =>
      Ok(Json.toJson(r)(Writes.seq(writes)))
    }
  }


  def assignmentSubmissions: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.tabulaAssignmentSubmissionGenerator(tabulaSubmissionGenaratorForm))
  }

  def generateAssignmentSubmissions(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    tabulaSubmissionGenaratorForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest),
      data => {
        val assessmentId = UUID.fromString(data.assessmentId)
        ServiceResults.zip(
          assessmentService.get(assessmentId),
          studentAssessmentService.byAssessmentId(assessmentId)
        ).successFlatMap { case (assessment, studentAssessments) =>
          val filteredStudentAssessments = data.studentId match {
            case Some(student) =>  studentAssessments.filter(_.studentId == student).headOption
            case _ =>  None
          }
          tabulaAssessments.generateAssignmentSubmissions(assessment, filteredStudentAssessments).successMap { studentAssessments =>
            if(studentAssessments.isEmpty) {
              val formWithError = tabulaSubmissionGenaratorForm.withError(FormError("", "error.tabula.assessment.invalid"))
                Ok(views.html.sysadmin.tabulaAssignmentSubmissionGenerator(formWithError))
            } else {
              Redirect(routes.AdminAssessmentsController.view(assessment.id)).flashing {
                "success" -> Messages("flash.studentAssessment.updated.count", studentAssessments.size)
              }
            }
          }
        }
      }
    )
  }

  private val redirectHome = Redirect(controllers.sysadmin.routes.SysadminTestController.home())

}
