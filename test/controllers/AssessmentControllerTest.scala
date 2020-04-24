package controllers

import java.io.File
import java.time.Duration
import java.util.UUID

import controllers.AssessmentController.{FinishExamFormData, UploadFilesFormData}
import domain.Assessment.Platform
import domain.dao.{AssessmentDao, StudentAssessmentDao, UploadedFileDao}
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.{Assessment, Declarations, Fixtures, StudentAssessment}
import helpers.{CleanUpDatabaseAfterEachTest, Scenario, SimpleSemanticRelativeTime}
import play.api.mvc._
import play.api.test.Helpers._
import services.{AssessmentService, StudentAssessmentService, UploadedFileService}
import specs.BaseSpec
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

//    "Allow a user to download a file they submitted" in new FileUploadedScenario() { s =>
//      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
//      status(resDownloadAttachment) mustBe OK
//      htmlErrors(resDownloadAttachment) mustBe empty
//    }

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

  def reqFinish(assessment: Assessment, user: User, data: FinishExamFormData): Future[Result] =
    req(controller.finish(assessment.id).url)
      .forUser(user)
      .post(Seq("agreeDisclaimer" -> data.agreeDisclaimer.toString))

}
