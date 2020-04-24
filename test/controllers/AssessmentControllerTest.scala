package controllers

import java.io.File
import java.time.Duration
import java.util.UUID

import controllers.AssessmentController.UploadFilesFormData
import domain.Assessment.Platform
import domain.dao.{AssessmentDao, StudentAssessmentDao, UploadedFileDao}
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.{StoredStudentAssessment}
import domain.{Assessment, Declarations, Fixtures, StudentAssessment}
import helpers.{CleanUpDatabaseAfterEachTest, Scenario, SimpleSemanticRelativeTime}
import play.api.mvc._
import play.api.test.Helpers._
import services.{AssessmentService, StudentAssessmentService, UploadedFileService}
import specs.BaseSpec
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, User}

import scala.concurrent.Future

class AssessmentControllerTest extends BaseSpec with CleanUpDatabaseAfterEachTest with SimpleSemanticRelativeTime {

  private val assessmentDao = get[AssessmentDao]
  private val studentAssessmentDao = get[StudentAssessmentDao]
  private val assessmentService = get[AssessmentService]
  private val studentAssessmentService = get[StudentAssessmentService]
  private val uploadedFileService = get[UploadedFileService]
  private val uploadedFileDao = get[UploadedFileDao]
  private val RupertsSubmission: File = new File(getClass.getResource(Fixtures.uploadedFiles.specialJPG.path).getFile)

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

    "Prevent a user from uploading a file to someone else's assessment" in new AssessmentStartedScenario() { s =>
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
      private val resDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Herbert)
      status(resDeleteFile) mustBe NOT_FOUND
    }

    "Prevent a user from uploading a duplicate of an already uploaded file" in new FileUploadedScenario() { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe BAD_REQUEST
      contentAsString(resFileUpload) must include("which already exists")
    }

//    "Allow a user to download a file they submitted" in new FileUploadedScenario() { s =>
//      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
//      status(resDownloadAttachment) mustBe OK
//      htmlErrors(resDownloadAttachment) mustBe empty
//    }
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

    // Herbert Crest is a naughty student who wants to look at Rupert's exam!
    val Herbert: User = Fixtures.users.student2

    val TheAssessment: Assessment = assessmentService.get(assessmentId).futureValue.toOption.get
  }

  class AssessmentNotStartedScenario extends BasicSittingScenario {
    private val storedStudentAssessment: StoredStudentAssessment =
      Fixtures.studentAssessments.storedStudentAssessment(
        assessmentId,
        RupertsId
      )
    execWithCommit(studentAssessmentDao.insert(storedStudentAssessment))
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
    // Faffy way of uploading file to get round troublesome audit log context
    private val fileId = UUID.randomUUID
    private val storedFile = Fixtures.uploadedFiles.storedUploadedStudentAssessmentFile(
      studentAssessmentId = RupertsAssessment.id,
      id = fileId,
      createTime = now,
    )
    execWithCommit(uploadedFileDao.insert(storedFile))

    val RupertsUploadedFile: UploadedFile = uploadedFileService.get(fileId).futureValue.toOption.get
    private val updatedStudentAssessment = RupertsStartedAssessment.copy(
      uploadedFiles = Seq(RupertsUploadedFile)
    )
    studentAssessmentService.upsert(updatedStudentAssessment).futureValue

    val RupertsAssessmentWithFile: StudentAssessment = studentAssessmentService.get(RupertsId, TheAssessment.id).futureValue.toOption.get
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

}
