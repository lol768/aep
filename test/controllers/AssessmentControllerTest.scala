package controllers

import java.io.File
import java.time.Duration
import java.util.UUID

import akka.stream.Materializer
import controllers.AssessmentController.{FinishExamFormData, UploadFilesFormData}
import domain.Assessment.{DurationStyle, Platform}
import domain.Assessment.DurationStyle._
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
import system.Features
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
  private val features = get[Features]
  private val RupertsSubmission: File = new File(getClass.getResource(Fixtures.uploadedFiles.specialJPG.path).getFile)
  private val MindysSubmission: File = new File(getClass.getResource(Fixtures.uploadedFiles.specialJPG.path).getFile)

  implicit val mat: Materializer = get[Materializer]

  // Strings looked for in response by tests:
  private val authorshipHeader = "<h1>Declaration and statement of authorship</h1>"
  private val reasonableAdjustmentsHeader = "<h1>Reasonable Adjustments</h1>"
  private val notStartedMessage = "This assessment has not been started."
  private val noFileMessage = "Please attach a file"
  private val fileExistsMessage = "which already exists"
  private val untickedDisclaimerMessage = "I understand that this action is final and that I won't be able to make further submissions. You must tick the box to finish the assessment."
  private val alreadyFinalisedMessage = "You've already finalised your submission to this assessment"
  private val fileUploadFormElement = "<input autocomplete=\"off\" type=\"file\" name=\"file\" multiple>"
  private val lateUploadWarning = "If you upload new files at this point your submission may be considered as late."
  private val canNoLongerModifyMessage = "You can no longer modify your submission. The latest time you could make changes has passed."
  private val canNoLongerStartMessage = "You can no longer start this assessment."

  "AssessmentController" should {
    "Allow a student to view the assessment they have scheduled (DayWindow assessment)" in new AssessmentNotStartedScenario(DayWindow) { s =>
      private val resView = reqView(s.TheAssessment, s.Rupert)
      status(resView) mustBe OK
      htmlErrors(resView) mustBe empty
    }

    "Allow a student to view the assessment they have scheduled (FixedStart assessment)" in new AssessmentNotStartedScenario(FixedStart) { s =>
      private val resView = reqView(s.TheAssessment, s.Rupert)
      status(resView) mustBe OK
      htmlErrors(resView) mustBe empty
    }

    "Not allow a student to view an assessment that they're not scheduled to take" in new AssessmentNotStartedScenario(DayWindow) { s =>
      private val resView = reqView(s.TheAssessment, s.Herbert)
      status(resView) mustBe NOT_FOUND
    }

    "Show the authorship declaration form if it has not been accepted already (DayWindow assessment)" in new AssessmentNotStartedScenario(DayWindow) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include(authorshipHeader)
    }

    "Show the authorship declaration form if it has not been accepted already (FixedStart assessment)" in new AssessmentNotStartedScenario(FixedStart) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include(authorshipHeader)
    }

    "Not allow a student to start an assessment that they're not scheduled to take" in new AssessmentNotStartedScenario(DayWindow) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Herbert)
      status(resStart) mustBe NOT_FOUND
    }

    "Show the reasonable adjustments form if it has not been declared yet (DayWindow assessment)" in new OnlyAuthorshipDeclarationAcceptedScenario(DayWindow) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include(reasonableAdjustmentsHeader)
    }

    "Show the reasonable adjustments form if it has not been declared yet (FixedStart assessment)" in new OnlyAuthorshipDeclarationAcceptedScenario(FixedStart) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include(reasonableAdjustmentsHeader)
    }

    "Redirect the student to the assessment if all declarations have been accepted (DayWindow assessment)" in new AllDeclarationsAcceptedScenario(DayWindow) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe SEE_OTHER
      header("Location", resStart).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Redirect the student to the assessment if all declarations have been accepted (FixedStart assessment)" in new AllDeclarationsAcceptedScenario(FixedStart) { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe SEE_OTHER
      header("Location", resStart).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Not allow upload of files to an assessment that has not yet started" in new OnlyAuthorshipDeclarationAcceptedScenario(DayWindow) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe FORBIDDEN
      contentAsString(resFileUploadRupert) must include(notStartedMessage)

      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe FORBIDDEN
      contentAsString(resFileUploadMindy) must include(notStartedMessage)
    }

    "Allow file uploads once assessment has started (DayWindow assessment)" in new AssessmentStartedScenario(DayWindow) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe OK
      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe OK
    }

    "Allow file uploads once assessment has started (FixedStart assessment)" in new AssessmentStartedScenario(FixedStart) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe OK
      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe OK
    }

    "Prevent a user from uploading a file to an assessment they're not scheduled to take" in new AssessmentStartedScenario(DayWindow) { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Herbert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe NOT_FOUND
    }

    "Warn a user if they upload no files at all" in new AssessmentStartedScenario(DayWindow) { s =>
      private val resNoFileUpload = reqNoFileUpload(s.TheAssessment, s.Rupert, UploadFilesFormData(xhr = true))
      status(resNoFileUpload) mustBe BAD_REQUEST
      contentAsString(resNoFileUpload) must include(noFileMessage)
    }

    "Allow a user to delete a file submission while the assessment is ongoing (DayWindow assessment)" in new FileUploadedScenario(DayWindow) { s =>
      private val resDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFile) mustBe SEE_OTHER
      header("Location", resDeleteFile).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Allow a user to delete a file submission while the assessment is ongoing (FixedStart assessment)" in new FileUploadedScenario(FixedStart) { s =>
      private val resDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFile) mustBe SEE_OTHER
      header("Location", resDeleteFile).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Prevent a user from deleting somebody else's submitted file" in new FileUploadedScenario(DayWindow) { s =>
      private val herbertResDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Herbert)
      status(herbertResDeleteFile) mustBe NOT_FOUND

      private val berthaResDeleteFile = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Bertha)
      status(berthaResDeleteFile) mustBe FORBIDDEN
    }

    "Prevent a user from uploading a duplicate of an already uploaded file" in new FileUploadedScenario(DayWindow) { s =>
      private val resFileUpload = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUpload) mustBe BAD_REQUEST
      contentAsString(resFileUpload) must include(fileExistsMessage)
    }

    "Allow a user to download a file they submitted" in new FileUploadedScenario(DayWindow) { s =>
      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDownloadAttachment) mustBe OK
      htmlErrors(resDownloadAttachment) mustBe empty
    }

    "Prevent a user from downloading someone else's submitted file" in new FileUploadedScenario(DayWindow) { s =>
      private val herbertResDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Herbert)
      status(herbertResDownloadAttachment) mustBe NOT_FOUND

      private val berthaResDownloadAttachemnt = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Bertha)
      status(berthaResDownloadAttachemnt) mustBe FORBIDDEN
    }

    "Prevent a user from finalising an assessment if the disclaimer is not agreed" in new FileUploadedScenario(DayWindow) { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = false))
      status(resFinish) mustBe BAD_REQUEST
      htmlErrors(resFinish) must contain(untickedDisclaimerMessage)
    }

    "Prevent a user from finalising an assessment they're not scheduled to take" in new FileUploadedScenario(DayWindow) { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Herbert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe NOT_FOUND
    }

    "Allow a user to finalise an unfinalised assessment if it's not late and disclaimer is agreed (DayWindow assessment)" in new FileUploadedScenario(DayWindow) { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe SEE_OTHER
      header("Location", resFinish).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Allow a user to finalise an unfinalised assessment if it's not late and disclaimer is agreed (FixedStart assessment)" in new FileUploadedScenario(FixedStart) { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe SEE_OTHER
      header("Location", resFinish).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }

    "Prevent a user from finalising an already finalised assessment" in new FinishedAssessmentScenario(DayWindow) { s =>
      private val resFinish = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinish) mustBe FORBIDDEN
      contentAsString(resFinish) must include(alreadyFinalisedMessage)
    }

    "Still display the assessment view as normal to a user during the 45 minute grace period (DayWindow assessment)" in new StudentIntoGracePeriodScenario(DayWindow) { s =>
      private val resViewRupert = reqView(s.TheAssessment, s.Rupert)
      status(resViewRupert) mustBe OK
      contentAsString(resViewRupert) must include(fileUploadFormElement)
      contentAsString(resViewRupert) mustNot include(lateUploadWarning)

      private val resViewMindy = reqView(s.TheAssessment, s.Mindy)
      status(resViewMindy) mustBe OK
      contentAsString(resViewMindy) must include(fileUploadFormElement)
      contentAsString(resViewMindy) mustNot include(lateUploadWarning)
    }

    "Still display the assessment view as normal to a user during the 45 minute grace period (FixedStart assessment)" in new StudentIntoGracePeriodScenario(FixedStart) { s =>
      private val resViewRupert = reqView(s.TheAssessment, s.Rupert)
      status(resViewRupert) mustBe OK
      contentAsString(resViewRupert) must include(fileUploadFormElement)
      contentAsString(resViewRupert) mustNot include(lateUploadWarning)

      private val resViewMindy = reqView(s.TheAssessment, s.Mindy)
      status(resViewMindy) mustBe OK
      contentAsString(resViewMindy) must include(fileUploadFormElement)
      contentAsString(resViewMindy) mustNot include(lateUploadWarning)
    }

    "Still display the assessment view during the late submission period but warn submission will be marked late if late period is active (DayWindow assessment)" in new StudentIntoLatePeriodScenario(DayWindow) { s =>
      private val resView = reqView(s.TheAssessment, s.Rupert)
      status(resView) mustBe OK
      if (!features.importStudentExtraTime) {
        contentAsString(resView) must include(fileUploadFormElement)
        contentAsString(resView) must include(lateUploadWarning)
      }
    }

    "Still display the assessment view during the late submission period but warn submission will be marked late if late period is active (FixedStart assessment)" in new StudentIntoLatePeriodScenario(FixedStart) { s =>
      private val resView = reqView(s.TheAssessment, s.Rupert)
      status(resView) mustBe OK
      if (!features.importStudentExtraTime) {
        contentAsString(resView) must include(fileUploadFormElement)
        contentAsString(resView) must include(lateUploadWarning)
      }
    }

    "No longer display the file upload form once the grace and late periods have passed (DayWindow assessment)" in new StudentMissedDeadlineScenario(DayWindow) { s =>
      private val resViewRupert = reqView(s.TheAssessment, s.Rupert)
      status(resViewRupert) mustBe OK
      contentAsString(resViewRupert) mustNot include(fileUploadFormElement)

      private val resViewMindy = reqView(s.TheAssessment, s.Mindy)
      status(resViewMindy) mustBe OK
      contentAsString(resViewMindy) mustNot include(fileUploadFormElement)
    }

    "No longer display the file upload form once the grace and late periods have passed (FixedStart assessment)" in new StudentMissedDeadlineScenario(FixedStart) { s =>
      private val resViewRupert = reqView(s.TheAssessment, s.Rupert)
      status(resViewRupert) mustBe OK
      contentAsString(resViewRupert) mustNot include(fileUploadFormElement)

      private val resViewMindy = reqView(s.TheAssessment, s.Mindy)
      status(resViewMindy) mustBe OK
      contentAsString(resViewMindy) mustNot include(fileUploadFormElement)
    }

    "No longer allow submission of files when the deadline has passed (DayWindow assessment)" in new StudentMissedDeadlineScenario(DayWindow) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe FORBIDDEN
      contentAsString(resFileUploadRupert) must include(canNoLongerModifyMessage)

      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe FORBIDDEN
      contentAsString(resFileUploadMindy) must include(canNoLongerModifyMessage)
    }

    "No longer allow submission of files when the deadline has passed (FixedStart assessment)" in new StudentMissedDeadlineScenario(FixedStart) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe FORBIDDEN
      contentAsString(resFileUploadRupert) must include(canNoLongerModifyMessage)

      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe FORBIDDEN
      contentAsString(resFileUploadMindy) must include(canNoLongerModifyMessage)
    }

    "No longer allow deletion of files when the deadline has passed (DayWindow assessment)" in new StudentUploadedFileButMissedDeadlineScenario(DayWindow) { s =>
      private val resDeleteFileRupert = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFileRupert) mustBe FORBIDDEN
      contentAsString(resDeleteFileRupert) must include(alreadyFinalisedMessage)

      private val resDeleteFileMindy = reqDeleteFile(s.TheAssessment, s.MindysUploadedFile, s.Mindy)
      status(resDeleteFileMindy) mustBe FORBIDDEN
      contentAsString(resDeleteFileMindy) must include(alreadyFinalisedMessage)
    }

    "No longer allow deletion of files when the deadline has passed (FixedStart assessment)" in new StudentUploadedFileButMissedDeadlineScenario(FixedStart) { s =>
      private val resDeleteFileRupert = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFileRupert) mustBe FORBIDDEN
      contentAsString(resDeleteFileRupert) must include(alreadyFinalisedMessage)

      private val resDeleteFileMindy = reqDeleteFile(s.TheAssessment, s.MindysUploadedFile, s.Mindy)
      status(resDeleteFileMindy) mustBe FORBIDDEN
      contentAsString(resDeleteFileMindy) must include(alreadyFinalisedMessage)
    }

    "No longer allow finalising of assessment when the deadline has passed (DayWindow assessment)" in new StudentUploadedFileButMissedDeadlineScenario(DayWindow) { s =>
      private val resFinishRupert = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishRupert) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the deadline passes
      contentAsString(resFinishRupert) must include(alreadyFinalisedMessage)

      private val resFinishMindy = reqFinish(s.TheAssessment, s.Mindy, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishMindy) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the deadline passes
      contentAsString(resFinishMindy) must include(alreadyFinalisedMessage)
    }

    "No longer allow finalising of assessment when the deadline has passed (FixedStart assessment)" in new StudentUploadedFileButMissedDeadlineScenario(FixedStart) { s =>
      private val resFinishRupert = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishRupert) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the deadline passes
      contentAsString(resFinishRupert) must include(alreadyFinalisedMessage)

      private val resFinishMindy = reqFinish(s.TheAssessment, s.Mindy, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishMindy) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the deadline passes
      contentAsString(resFinishMindy) must include(alreadyFinalisedMessage)
    }

    "Still allow download of files when the deadline has passed (DayWindow assessment)" in new StudentUploadedFileButMissedDeadlineScenario(DayWindow) { s =>
      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDownloadAttachment) mustBe OK
    }

    "Still allow download of files when the deadline has passed (FixedStart assessment)" in new StudentUploadedFileButMissedDeadlineScenario(FixedStart) { s =>
      private val resDownloadAttachment = reqDownloadAttachment(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDownloadAttachment) mustBe OK
    }

    "No longer allow submission of files when the 24 hour window has passed, ignoring the usual grace and late periods" in new EndOfWindowBeforeNormalDeadlineScenario(DayWindow) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe FORBIDDEN
      contentAsString(resFileUploadRupert) must include(canNoLongerModifyMessage)

      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe FORBIDDEN
      contentAsString(resFileUploadMindy) must include(canNoLongerModifyMessage)
    }

    "No longer allow deletion of files when the 24 hour window has passed, ignoring the usual grace and late periods" in new EndOfWindowBeforeNormalDeadlineScenario(DayWindow) { s =>
      private val resDeleteFileRupert = reqDeleteFile(s.TheAssessment, s.RupertsUploadedFile, s.Rupert)
      status(resDeleteFileRupert) mustBe FORBIDDEN
      contentAsString(resDeleteFileRupert) must include(canNoLongerModifyMessage)

      private val resDeleteFileMindy = reqDeleteFile(s.TheAssessment, s.MindysUploadedFile, s.Mindy)
      status(resDeleteFileMindy) mustBe FORBIDDEN
      contentAsString(resDeleteFileMindy) must include(canNoLongerModifyMessage)
    }

    "No longer allow finalising of assessment when the 24 hour window has passed, ignoring the usual grace and late periods" in new EndOfWindowBeforeNormalDeadlineScenario(DayWindow) { s =>
      private val resFinishRupert = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishRupert) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the end of the window passes
      contentAsString(resFinishRupert) must include(canNoLongerModifyMessage)

      private val resFinishMindy = reqFinish(s.TheAssessment, s.Mindy, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishMindy) mustBe FORBIDDEN
      // Assessment should be auto-finalised when the end of the window passes
      contentAsString(resFinishMindy) must include(canNoLongerModifyMessage)
    }

    "Not allow a student to start their DayWindow assessment if they've completely missed the 24 hour window" in new StudentCompletelyMissedWindowScenario(DayWindow) { s =>
      private val resStartRupert = reqStart(s.TheAssessment, s.Rupert)
      status(resStartRupert) mustBe FORBIDDEN
      contentAsString(resStartRupert) must include(canNoLongerStartMessage)

      private val resStartMindy = reqStart(s.TheAssessment, s.Mindy)
      status(resStartMindy) mustBe FORBIDDEN
      contentAsString(resStartMindy) must include(canNoLongerStartMessage)
    }

    "Not allow a student to start their FixedStart assessment if they've missed the absolute deadline" in new StudentCompletelyMissedWindowScenario(FixedStart) { s =>
      private val resStartRupert = reqStart(s.TheAssessment, s.Rupert)
      status(resStartRupert) mustBe FORBIDDEN
      contentAsString(resStartRupert) must include(canNoLongerStartMessage)

      private val resStartMindy = reqStart(s.TheAssessment, s.Mindy)
      status(resStartMindy) mustBe FORBIDDEN
      contentAsString(resStartMindy) must include(canNoLongerStartMessage)
    }

    "Not allow a student to upload a file if they've completely missed the 24 hour window" in new StudentCompletelyMissedWindowScenario(DayWindow) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe FORBIDDEN
      contentAsString(resFileUploadRupert) must include(notStartedMessage)

      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe FORBIDDEN
      contentAsString(resFileUploadMindy) must include(notStartedMessage)
    }

    "Not allow a student to upload a file if they've completely missed an absolute FixedStart deadline" in new StudentCompletelyMissedWindowScenario(FixedStart) { s =>
      private val resFileUploadRupert = reqFileUpload(s.TheAssessment, RupertsSubmission, s.Rupert, UploadFilesFormData(xhr = true))
      status(resFileUploadRupert) mustBe FORBIDDEN
      contentAsString(resFileUploadRupert) must include(notStartedMessage)

      private val resFileUploadMindy = reqFileUpload(s.TheAssessment, MindysSubmission, s.Mindy, UploadFilesFormData(xhr = true))
      status(resFileUploadMindy) mustBe FORBIDDEN
      contentAsString(resFileUploadMindy) must include(notStartedMessage)
    }

    "Not allow finalising of assessment if the 24 hour window has been completely missed" in new StudentCompletelyMissedWindowScenario(DayWindow) { s =>
      private val resFinishRupert = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishRupert) mustBe FORBIDDEN
      contentAsString(resFinishRupert) must include(notStartedMessage)

      private val resFinishMindy = reqFinish(s.TheAssessment, s.Mindy, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishMindy) mustBe FORBIDDEN
      contentAsString(resFinishMindy) must include(notStartedMessage)
    }

    "Not allow finalising of assessment if the absolute FixedStart deadline has been completely missed" in new StudentCompletelyMissedWindowScenario(FixedStart) { s =>
      private val resFinishRupert = reqFinish(s.TheAssessment, s.Rupert, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishRupert) mustBe FORBIDDEN
      contentAsString(resFinishRupert) must include(notStartedMessage)

      private val resFinishMindy = reqFinish(s.TheAssessment, s.Mindy, FinishExamFormData(agreeDisclaimer = true))
      status(resFinishMindy) mustBe FORBIDDEN
      contentAsString(resFinishMindy) must include(notStartedMessage)
    }

  }

  class BasicSittingScenario(durationStyle: DurationStyle) extends Scenario(scenarioCtx) {
    val assessmentId: UUID = UUID.randomUUID
    private val storedAssessment: StoredAssessment =
      Fixtures.assessments.storedAssessment(
        uuid = assessmentId,
        platformOption = Some(Platform.OnlineExams),
        duration = Some(Duration.ofHours(3L)),
        durationStyle = durationStyle,
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

    // Mindy Dewilliger is a nice young lady with an extra 10 minutes per hour agreed for her assessments
    val Mindy: User = Fixtures.users.student4
    val MindysId: UniversityID = Mindy.universityId.get

    val TheAssessment: Assessment = assessmentService.get(assessmentId).futureValue.toOption.get
  }

  class AssessmentNotStartedScenario(durationStyle: DurationStyle)
    extends BasicSittingScenario(durationStyle) {
    private val storedStudentAssessments: Set[StoredStudentAssessment] =
      Set(RupertsId, BerthasId, MindysId).map { uid =>
        Fixtures.studentAssessments.storedStudentAssessment(
          assessmentId,
          uid,
          hourlyExtraTime = if (uid == MindysId) Some(Some(Duration.ofMinutes(10L))) else None
        )
      }
    execWithCommit(studentAssessmentDao.insertAll(storedStudentAssessments))
    val RupertsAssessment: StudentAssessment = studentAssessmentService.get(RupertsId, assessmentId).futureValue.toOption.get
    val MindysAssessment: StudentAssessment = studentAssessmentService.get(MindysId, assessmentId).futureValue.toOption.get
  }

  class OnlyAuthorshipDeclarationAcceptedScenario(durationStyle: DurationStyle)
    extends AssessmentNotStartedScenario(durationStyle) {
    Seq(RupertsAssessment, MindysAssessment).foreach { assessment =>
      val declarations = Declarations(
        assessment.id,
        acceptsAuthorship = true,
      )
      studentAssessmentService.upsert(declarations).futureValue
    }

  }

  class AllDeclarationsAcceptedScenario(durationStyle: DurationStyle)
    extends AssessmentNotStartedScenario(durationStyle) {
    Seq(RupertsAssessment, MindysAssessment).foreach { assessment =>
      val declarations = Declarations(
        assessment.id,
        acceptsAuthorship = true,
        completedRA = true,
      )
      studentAssessmentService.upsert(declarations).futureValue
    }

  }

  class AssessmentStartedScenario(durationStyle: DurationStyle)
    extends AllDeclarationsAcceptedScenario(durationStyle) {
    studentAssessmentService.startAssessment(RupertsAssessment).futureValue
    val RupertsStartedAssessment: StudentAssessment = studentAssessmentService.get(RupertsId, assessmentId).futureValue.toOption.get
    studentAssessmentService.startAssessment(MindysAssessment).futureValue
    val MindysStartedAssessment: StudentAssessment = studentAssessmentService.get(MindysId, assessmentId).futureValue.toOption.get
  }

  class FileUploadedScenario(durationStyle: DurationStyle)
    extends AssessmentStartedScenario(durationStyle) {
    private val RupertsAuditLogContext: AuditLogContext =
      AuditLogContext.empty().copy(usercode = Some(Rupert.usercode))

    private val MindysAuditLogContext: AuditLogContext =
      AuditLogContext.empty().copy(usercode = Some(Mindy.usercode))

    val RupertsUploadedFile: UploadedFile = uploadedFileService.store(
      FileResourceUtils.byteSourceResource(specialJPG.path),
      specialJPG.uploadedFileSave,
      RupertsAssessment.id,
      UploadedFileOwner.StudentAssessment
    )(RupertsAuditLogContext).futureValue.getOrElse(fail("bad service result"))

    val MindysUploadedFile: UploadedFile = uploadedFileService.store(
      FileResourceUtils.byteSourceResource(specialJPG.path),
      specialJPG.uploadedFileSave,
      MindysAssessment.id,
      UploadedFileOwner.StudentAssessment
    )(MindysAuditLogContext).futureValue.getOrElse(fail("bad service result"))

    private val rupertsUpdatedStudentAssessment = RupertsStartedAssessment.copy(
      uploadedFiles = Seq(RupertsUploadedFile)
    )
    private val mindysUpdatedStudentAssessment = MindysStartedAssessment.copy(
      uploadedFiles = Seq(MindysUploadedFile)
    )

    studentAssessmentService.upsert(rupertsUpdatedStudentAssessment).futureValue
    studentAssessmentService.upsert(mindysUpdatedStudentAssessment).futureValue

    val RupertsAssessmentWithFile: StudentAssessment = studentAssessmentService.get(RupertsId, TheAssessment.id).futureValue.toOption.get
    val MindysAssessmentWithFile: StudentAssessment = studentAssessmentService.get(MindysId, TheAssessment.id).futureValue.toOption.get
  }

  class FinishedAssessmentScenario(durationStyle: DurationStyle)
    extends FileUploadedScenario(durationStyle) {
    studentAssessmentService.finishAssessment(RupertsAssessment).futureValue
    studentAssessmentService.finishAssessment(MindysAssessment).futureValue
  }

  class StudentIntoGracePeriodScenario(durationStyle: DurationStyle)
    extends AssessmentStartedScenario(durationStyle) {

    private val assessmentStart = durationStyle match {
      case DayWindow => Some(5.hours ago)
      case FixedStart => Some(3.hours and 10.minutes ago)
    }

    private val rupertStart = durationStyle match {
      case DayWindow => Some(3.hours and 10.minutes ago)
      case FixedStart => Some(30.minutes ago)
    }

    private val mindyStart = durationStyle match {
      case DayWindow => Some(3.hours and 40.minutes ago)
      case FixedStart => Some(30.minutes ago)
    }

    assessmentService.update(
      TheAssessment.copy(startTime = assessmentStart),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = rupertStart,
      )
    ).futureValue
    studentAssessmentService.upsert(
      MindysAssessment.copy(
        startTime = mindyStart
      )
    ).futureValue
  }

  class StudentIntoLatePeriodScenario(durationStyle: DurationStyle) extends
    AssessmentStartedScenario(durationStyle) {

    private val assessmentStart = Some(5.hours ago)

    private val rupertStart = durationStyle match {
      case DayWindow => Some(4.hours ago)
      case FixedStart => Some(10.minutes ago)
    }

    private val mindyStart = durationStyle match {
      case DayWindow => Some(4.hours and 35.minutes ago)
      case FixedStart => Some(10.minutes ago)
    }

    assessmentService.update(
      TheAssessment.copy(startTime = assessmentStart),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = rupertStart
      )
    ).futureValue
    studentAssessmentService.upsert(
      MindysAssessment.copy(
        startTime = mindyStart
      )
    ).futureValue
  }

  class StudentMissedDeadlineScenario(durationStyle: DurationStyle)
    extends AssessmentStartedScenario(durationStyle) {

    private val assessmentStart = durationStyle match {
      case DayWindow => Some(12.hours ago)
      case FixedStart => Some(6.hours ago)
    }

    private val rupertStart = durationStyle match {
      case DayWindow => Some(7.hours ago)
      case FixedStart => Some(5.hours and 58.minutes ago)
    }

    private val mindyStart = durationStyle match {
      case DayWindow => Some(7.hours and 35.minutes ago)
      case FixedStart => Some(5.hours and 58.minutes ago)
    }

    assessmentService.update(
      TheAssessment.copy(startTime = assessmentStart),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = rupertStart
      )
    ).futureValue
    studentAssessmentService.upsert(
      MindysAssessment.copy(
        startTime = mindyStart
      )
    ).futureValue
  }

  class StudentUploadedFileButMissedDeadlineScenario(durationStyle: DurationStyle)
    extends FileUploadedScenario(durationStyle) {

    private val assessmentStart = durationStyle match {
      case DayWindow => Some(12.hours ago)
      case FixedStart => Some(6.hours ago)
    }

    private val rupertStart = durationStyle match {
      case DayWindow => Some(7.hours ago)
      case FixedStart => Some(5.hours and 58.minutes ago)
    }

    private val mindyStart = durationStyle match {
      case DayWindow => Some(7.hours and 35.minutes ago)
      case FixedStart => Some(5.hours and 58.minutes ago)
    }

    assessmentService.update(
      TheAssessment.copy(startTime = assessmentStart),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessmentWithFile.copy(
        startTime = rupertStart
      )
    ).futureValue
    studentAssessmentService.upsert(
      MindysAssessmentWithFile.copy(
        startTime = mindyStart
      )
    ).futureValue
  }

  class EndOfWindowBeforeNormalDeadlineScenario(durationStyle: DurationStyle)
    extends FileUploadedScenario(durationStyle) {

    assessmentService.update(
      TheAssessment.copy(startTime = Some(25.hours ago)),
      Seq.empty
    ).futureValue
    studentAssessmentService.upsert(
      RupertsAssessment.copy(
        startTime = Some(1.hour and 10.minutes ago)
      )
    ).futureValue
    studentAssessmentService.upsert(
      MindysAssessment.copy(
        startTime = Some(1.hour and 10.minutes ago)
      )
    ).futureValue
  }

  class StudentCompletelyMissedWindowScenario(durationStyle: DurationStyle)
    extends AssessmentNotStartedScenario(durationStyle) {

    private val startTime = durationStyle match {
      case DayWindow => Some(25.hours ago)
      case FixedStart => Some(8.hours ago)
    }
    assessmentService.update(
      TheAssessment.copy(startTime = startTime),
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
