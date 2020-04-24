package controllers

import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

import akka.stream.Materializer
import controllers.AssessmentController.{FinishExamFormData, UploadFilesFormData}
import domain.Assessment.Platform
import domain.Fixtures.uploadedFiles.specialJPG
import domain.dao.{AssessmentDao, StudentAssessmentDao}
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.{Assessment, Declarations, Fixtures, StudentAssessment, UploadedFileOwner}
import helpers.{CleanUpDatabaseAfterEachTest, FileResourceUtils, Scenario, SimpleSemanticRelativeTime}
import play.api.mvc._
import play.api.test.Helpers._
import services.{AssessmentService, StudentAssessmentService, UploadedFileService}
import specs.BaseSpec
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, User}

import scala.language.postfixOps
import scala.concurrent.Future

class AssessmentControllerTest extends BaseSpec with CleanUpDatabaseAfterEachTest with SimpleSemanticRelativeTime {

  private val assessmentDao = get[AssessmentDao]
  private val studentAssessmentDao = get[StudentAssessmentDao]
  private val assessmentService = get[AssessmentService]
  private val studentAssessmentService = get[StudentAssessmentService]
  private val uploadedFileService = get[UploadedFileService]
  private val RupertsSubmission: File = new File(getClass.getResource(Fixtures.uploadedFiles.specialJPG.path).getFile)

  implicit val mat: Materializer = get[Materializer]

  "AssessmentController" should {
    "Allow a student to view the assessment they have scheduled" in new AssessmentNotStartedScenario() { s =>
      private val resView = reqView(s.TheAssessment, s.Rupert)
      status(resView) mustBe OK
      htmlErrors(resView) mustBe empty
    }

    "Not allow a student to view an assessment that they're not scheduled to take" in new AssessmentNotStartedScenario() { s =>
      private val resView = reqView(s.TheAssessment, s.Herbert)
      status(resView) mustBe NOT_FOUND
    }

    "Show the authorship declaration form if it has not been accepted already" in new AssessmentNotStartedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include("<h1>Declaration and statement of authorship</h1>")
    }

    "Not allow a student to start an assessment that they're not scheduled to take" in new AssessmentNotStartedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Herbert)
      status(resStart) mustBe NOT_FOUND
    }

    "Show the reasonable adjustments form if it has not been declared yet" in new OnlyAuthorshipDeclarationAcceptedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include("<h1>Reasonable Adjustments</h1>")
    }

    "Redirect the student to the assessment if all declarations have been accepted" in new AllDeclarationsAcceptedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe SEE_OTHER
      header("Location", resStart).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Not allow upload of files to an assessment that has not yet started" in new OnlyAuthorshipDeclarationAcceptedScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe FORBIDDEN
      contentAsString(resFileUpload) must include("This assessment has not been started.")
    }

    "Allow file uploads once assessment has started" in new AssessmentStartedScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe OK
    }

    "Prevent a user from uploading a file to an assessment they're not scheduled to take" in new AssessmentStartedScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Herbert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe NOT_FOUND
    }

    "Warn a user if they upload no files at all" in new AssessmentStartedScenario { s =>
      private val resNoFileUpload = reqNoFileUpload(s.TheAssessment, s.Rupert, UploadFilesFormData(xhr = true))
      status(resNoFileUpload) mustBe BAD_REQUEST
      contentAsString(resNoFileUpload) must include("Please attach a file")
    }

    "Allow a user to delete a file submission while the assessment is ongoing" in new FileUploadedScenario() { s =>
      private val resDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFile) mustBe SEE_OTHER
      header("Location", resDeleteFile).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Prevent a user from deleting somebody else's submitted file" in new FileUploadedScenario { s =>
      private val herbertResDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Herbert)
      status(herbertResDeleteFile) mustBe NOT_FOUND

      private val berthaResDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Bertha)
      status(berthaResDeleteFile) mustBe FORBIDDEN
    }

    "Prevent a user from uploading a duplicate of an already uploaded file" in new FileUploadedScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe BAD_REQUEST
      contentAsString(resFileUpload) must include("which already exists")
    }

    "Allow a user to download a file they submitted" in new FileUploadedScenario() { s =>
      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDownloadAttachment) mustBe OK
      htmlErrors(resDownloadAttachment) mustBe empty
    }

    "Prevent a user from downloading someone else's submitted file" in new FileUploadedScenario() { s =>
      private val herbertResDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Herbert)
      status(herbertResDownloadAttachment) mustBe NOT_FOUND

      private val berthaResDownloadAttachemnt = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Bertha)
      status(berthaResDownloadAttachemnt) mustBe FORBIDDEN
    }

    "Prevent a user from finalising an assessment if the disclaimer is not agreed" in new FileUploadedScenario() { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = false))
      status(resFinish) mustBe BAD_REQUEST
      htmlErrors(resFinish) must contain("I understand that this action is final and that I won't be able to make further submissions. You must tick the box to finish the assessment.")
    }

    "Prevent a user from finalising an assessment they're not scheduled to take" in new FileUploadedScenario() { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Herbert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe NOT_FOUND
    }

    "Allow a user to finalise an unfinalised assessment if it's not late and disclaimer is agreed" in new FileUploadedScenario() { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe SEE_OTHER
      header("Location", resFinish).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Prevent a user from finalising an already finalised assessment" in new FinishedAssessmentScenario() { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe FORBIDDEN
      contentAsString(resFinish) must include("You've already finalised your submission to this assessment")
    }

//    "Still display the assessment view as normal to a user during the 45 minute grace period" in new StudentIntoGracePeriodScenario() { s =>
//      private val resView = reqView(s.TheAssessment, s.Rupert)
//      status(resView) mustBe OK
//    }
//
//    "Still display the assessment view during the late submission period" in new StudentIntoLatePeriodScenario() { s =>
//      private val resView = reqView(s.TheAssessment, s.Rupert)
//      status(resView) mustBe OK
//    }
//
//    "No longer display the assessment view in full once the grace and late periods have passed" in new StudentMissedDeadlineScenario() { s =>
//      private val resView = reqView(s.TheAssessment, s.Rupert)
//      status(resView) mustBe OK
//      contentAsString(resView) must include("bonk")
//    }

    "No longer allow submission of files when the deadline has passed" in new StudentMissedDeadlineScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe FORBIDDEN
      contentAsString(resFileUpload) must include("You can no longer modify your submission. The latest time you could make changes has passed.")
    }

    "No longer allow deletion of files when the deadline has passed" in new StudentUploadedFileButMissedDeadlineScenario() { s =>
      private val resDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFile) mustBe FORBIDDEN
      contentAsString(resDeleteFile) must include("You can no longer modify your submission. The latest time you could make changes has passed.")
    }

    "No longer allow finalising of assessment when the deadline has passed" in new StudentUploadedFileButMissedDeadlineScenario() { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the deadline passes
      contentAsString(resFinish) must include("You've already finalised your submission to this assessment, you cannot perform further actions.")
    }

    "Still allow download of files when the deadline has passed" in new StudentUploadedFileButMissedDeadlineScenario() { s =>
      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDownloadAttachment) mustBe OK
    }

    "No longer allow submission of files when the 24 hour window has passed, ignoring the usual grace and late periods" in new EndOfWindowBeforeNormalDeadlineScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe FORBIDDEN
      contentAsString(resFileUpload) must include("You can no longer modify your submission. The latest time you could make changes has passed.")
    }

    "No longer allow deletion of files when the 24 hour window has passed, ignoring the usual grace and late periods" in new EndOfWindowBeforeNormalDeadlineScenario() { s =>
      private val resDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFile) mustBe FORBIDDEN
      contentAsString(resDeleteFile) must include("You can no longer modify your submission. The latest time you could make changes has passed.")
    }

    "No longer allow finalising of assessment when the 24 hour window has passed, ignoring the usual grace and late periods" in new EndOfWindowBeforeNormalDeadlineScenario() { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the end of the window passes
      contentAsString(resFinish) must include("You can no longer modify your submission. The latest time you could make changes has passed.")
    }

    "Not allow a student to start their assessment if they've completely missed the 24 hour window" in new StudentCompletelyMissedWindowScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include("honkle")
    }

  }

  class BasicSittingScenario extends Scenario(scenarioCtx) {
    val assessmentId: UUID = UUID.randomUUID
    private val storedAssessment: StoredAssessment =
      Fixtures.assessments.storedAssessment(
        uuid = assessmentId,
        platformOption = Some(Platform.OnlineExams),
        duration = Some(Duration.ofHours(3L))
      ).copy(
        startTime = Some(2.hours ago)
      )
      execWithCommit(assessmentDao.insert(storedAssessment))

    // Rupert Hampole is sitting his assessment today
    val Rupert: User = Fixtures.users.student1
    val RupertsId: UniversityID = Rupert.universityId.get

    // Herbert Crest is a bumbling numpty who isn't even down to take this assessment
    val Herbert: User = Fixtures.users.student2

    // Bertha Schwine is a sinister character, taking the same assessment as Rupert, and determined to mess it up for him
    val Bertha: User = Fixtures.users.student3
    val BerthasId: UniversityID = Bertha.universityId.get

    val TheAssessment: Assessment = assessmentService.get(assessmentId).futureValue.toOption.get
  }

  class AssessmentNotStartedScenario extends BasicSittingScenario {
    private val storedStudentAssessments: Set[StoredStudentAssessment] =
      Set(RupertsId, BerthasId).map { uid =>
        Fixtures.studentAssessments.storedStudentAssessment(
          assessmentId,
          uid
        )
      }
    execWithCommit(studentAssessmentDao.insertAll(storedStudentAssessments))
    val RupertsAssessment: StudentAssessment = studentAssessmentService.get(RupertsId, assessmentId).futureValue.toOption.get
  }

  class OnlyAuthorshipDeclarationAcceptedScenario extends AssessmentNotStartedScenario {
    private val declarations = Declarations(
      RupertsAssessment.id,
      acceptsAuthorship = true,
    )
    studentAssessmentService.upsert(declarations).futureValue
  }

  class AllDeclarationsAcceptedScenario extends AssessmentNotStartedScenario {
    private val declarations = Declarations(
      RupertsAssessment.id,
      acceptsAuthorship = true,
      completedRA = true,
    )
    studentAssessmentService.upsert(declarations).futureValue
  }

  class AssessmentStartedScenario extends AllDeclarationsAcceptedScenario {
    studentAssessmentService.startAssessment(RupertsAssessment).futureValue
    val RupertsStartedAssessment: StudentAssessment = studentAssessmentService.get(RupertsId, assessmentId).futureValue.toOption.get
  }

  class FileUploadedScenario extends AssessmentStartedScenario {
    private val RupertsAuditLogContext: AuditLogContext =
      AuditLogContext.empty().copy(usercode = Some(Rupert.usercode))

    val RupertsUploadedFile: UploadedFile = uploadedFileService.store(
      FileResourceUtils.byteSourceResource(specialJPG.path),
      specialJPG.uploadedFileSave,
      RupertsAssessment.id,
      UploadedFileOwner.StudentAssessment
    )(RupertsAuditLogContext).futureValue.getOrElse(fail("bad service result"))

    private val updatedStudentAssessment = RupertsStartedAssessment.copy(
      uploadedFiles = Seq(RupertsUploadedFile)
    )
    studentAssessmentService.upsert(updatedStudentAssessment).futureValue

    val RupertsAssessmentWithFile: StudentAssessment = studentAssessmentService.get(RupertsId, TheAssessment.id).futureValue.toOption.get
  }

  class FinishedAssessmentScenario extends FileUploadedScenario {
    studentAssessmentService.finishAssessment(RupertsAssessment).futureValue
  }

  class StudentIntoGracePeriodScenario extends AssessmentStartedScenario {
    assessmentService.update(
      TheAssessment.copy(startTime = Some(5.hours ago)),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = Some(3.hours and 10.minutes ago),
      )
    ).futureValue
  }

  class StudentIntoLatePeriodScenario extends AssessmentStartedScenario {
    assessmentService.update(
      TheAssessment.copy(startTime = Some(5.hours ago)),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = Some(4.hours ago)
      )
    ).futureValue
  }

  class StudentMissedDeadlineScenario extends AssessmentStartedScenario {
    assessmentService.update(
      TheAssessment.copy(startTime = Some(12.hours ago)),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = Some(7.hours ago)
      )
    ).futureValue
  }

  class StudentUploadedFileButMissedDeadlineScenario extends FileUploadedScenario {
    assessmentService.update(
      TheAssessment.copy(startTime = Some(12.hours ago)),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessmentWithFile.copy(
        startTime = Some(7.hours ago)
      )
    ).futureValue
  }

  class EndOfWindowBeforeNormalDeadlineScenario extends FileUploadedScenario {
    assessmentService.update(
      TheAssessment.copy(startTime = Some(25.hours ago)),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = Some(1.hour and 10.minutes ago)
      )
    ).futureValue
  }

  class StudentCompletelyMissedWindowScenario extends AssessmentNotStartedScenario {
    assessmentService.update(
      TheAssessment.copy(startTime = Some(25.hours ago)),
      Seq.empty
    ).futureValue
  }

  private val controller = controllers.routes.AssessmentController

  def reqView(assessment: Assessment, user: User): Future[Result] =
    req(controller.view(assessment.id).url)
      .forUser(user)
      .get()

  def reqStart(assessment: Assessment, user: User): Future[Result] =
    req(controller.start(assessment.id).url)
      .forUser(user)
      .post(Seq.empty)

  def reqFileUpload(assessment: Assessment, file: File, user: User, data: UploadFilesFormData): Future[Result] =
    req(controller.uploadFiles(assessment.id).url)
      .forUser(user)
      .withFile(file)
      .postMultiPartForm(Seq("xhr" -> data.xhr.toString))

  def reqNoFileUpload(assessment: Assessment, user: User, data: UploadFilesFormData): Future[Result] =
    req(controller.uploadFiles(assessment.id).url)
      .forUser(user)
      .postMultiPartForm(Seq("xhr" -> data.xhr.toString))

  def reqDeleteFile(assessment: Assessment, file: UploadedFile, user: User): Future[Result] =
    req(controller.deleteFile(assessment.id, file.id).url)
      .forUser(user)
      .post(Seq.empty)

  def reqDownloadAttachment(assessment: Assessment, file: UploadedFile, user: User): Future[Result] =
    req(controller.downloadAttachment(assessment.id, file.id).url)
      .forUser(user)
      .get()

  def reqFinish(assessment: Assessment, user: User, data: FinishExamFormData): Future[Result] =
    req(controller.finish(assessment.id).url)
      .forUser(user)
      .post(Seq("agreeDisclaimer" -> data.agreeDisclaimer.toString))

}
