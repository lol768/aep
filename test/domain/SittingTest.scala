package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.{AssessmentType, Brief, Platform}
import domain.BaseSitting.ProgressState._
import domain.BaseSitting.SubmissionState
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}

class SittingTest extends PlaySpec with MockitoSugar {

  private case class Fixture (
    assessmentStart: Option[OffsetDateTime] = None,
    assessmentDuration: Option[Duration] = None,
    studentStart: Option[OffsetDateTime] = None,
    extraTimeAdjustment: Option[Duration] = None,
    finaliseTime: Option[OffsetDateTime] = None,
    uploadedFiles: Seq[UploadedFile] = Seq.empty,
  ) {

    val assessment: Assessment = Assessment(
      id = UUID.randomUUID(),
      paperCode = "heron-1",
      section = None,
      title = "Exposing the threats posed to civilisation by Ardea cinerea",
      startTime = assessmentStart,
      duration = assessmentDuration,
      platform = Set(Platform.OnlineExams),
      assessmentType = Some(AssessmentType.Bespoke),
      state = Assessment.State.Approved,
      tabulaAssessmentId = Some(UUID.randomUUID()),
      examProfileCode= "MAY2020",
      moduleCode = "IN-101",
      departmentCode = DepartmentCode("IN"),
      sequence = "E01",
      brief = Brief.empty,
      invigilators = Set.empty
    )

    val studentAssessment: StudentAssessment = StudentAssessment (
      assessmentId = assessment.id,
      id = UUID.randomUUID(),
      studentId = UniversityID("1431777"),
      inSeat = true,
      startTime = studentStart,
      extraTimeAdjustment = None,
      explicitFinaliseTime = None,
      uploadedFiles = uploadedFiles
    )

    val declarations: Declarations = Declarations(studentAssessment.id)

    val sitting: Sitting = Sitting(
      studentAssessment,
      assessment,
      declarations
    )
  }

  "Sitting#getProgressState" should {

    "Show as not yet open before the start date" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.plusDays(1)),
    ) {
      sitting.getProgressState mustBe Some(AssessmentNotYetOpen)
    }

    "Show the generic Started state for inProgress assessments without a duration" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(1)),
      assessmentDuration = Some(Duration.ofHours(3)),
      studentStart = Some(JavaTime.offsetDateTime.minusMinutes(30))
    ) {
      sitting.getProgressState mustBe Some(InProgress)
    }

    "Show InProgress assessments" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(1)),
      studentStart = Some(JavaTime.offsetDateTime.minusMinutes(30))
    ) {
      sitting.getProgressState mustBe Some(Started)
    }

    "Show as GracePeriod just after the main exam writing period" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(4)),
      assessmentDuration = Some(Duration.ofHours(3)),
      studentStart = Some(JavaTime.offsetDateTime.minusHours(3).minusMinutes(10))
    ) {
      // They had 3h+45m to submit, 3h+10m is in the grace upload period
      sitting.getProgressState mustBe Some(OnGracePeriod)
    }

    "Show as Late after the on time duration" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(4)),
      assessmentDuration = Some(Duration.ofHours(3)),
      studentStart = Some(JavaTime.offsetDateTime.minusHours(4))
    ) {
      // They had 3h+45m to submit, so 4h is in the "Late" section
      sitting.getProgressState mustBe Some(Late)
    }

    "Show as DeadlineMissed after even more time" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(4)),
      assessmentDuration = Some(Duration.ofHours(3)),
      studentStart = Some(JavaTime.offsetDateTime.minusHours(6))
    ) {
      // They had 3h+45m+2h to submit late, so 6h is right out
      sitting.getProgressState mustBe Some(DeadlineMissed)
    }

  }

  "Sitting#getSubmissionState" should {
    "Be None if there are no files" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(1)),
      studentStart = Some(JavaTime.offsetDateTime.minusMinutes(30))
    ) {
      sitting.getSubmissionState mustBe SubmissionState.None
    }

    "Be OnTime if all files on time" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(1)),
      assessmentDuration = Some(Duration.ofHours(3)),
      studentStart = Some(JavaTime.offsetDateTime.minusHours(4)),
      uploadedFiles = Seq(
        uploadedFile( // on time
          uploadStarted = JavaTime.offsetDateTime.minusHours(3),
          uploadFinished = JavaTime.offsetDateTime.minusHours(3) // ignore
        )
      )
    ) {
      sitting.getSubmissionState mustBe SubmissionState.OnTime
    }

    "Be Late if there are late files" in new Fixture (
      assessmentStart = Some(JavaTime.offsetDateTime.minusHours(1)),
      assessmentDuration = Some(Duration.ofHours(3)),
      studentStart = Some(JavaTime.offsetDateTime.minusHours(4)),
      uploadedFiles = Seq(
        uploadedFile( // on time
          uploadStarted = JavaTime.offsetDateTime.minusHours(3),
          uploadFinished = JavaTime.offsetDateTime.minusMinutes(10) // ignore
        ),
        uploadedFile( // late!
          uploadStarted = JavaTime.offsetDateTime.minusMinutes(5),
          uploadFinished = JavaTime.offsetDateTime.minusMinutes(3) // ignore
        )
      )
    ) {
      sitting.getSubmissionState mustBe SubmissionState.Late
    }






  }

  private def uploadedFile(uploadStarted: OffsetDateTime, uploadFinished: OffsetDateTime) =
    UploadedFile(
      id = UUID.randomUUID,
      fileName = "submission.pdf",
      contentLength = 1024,
      contentType = "application/pdf",
      uploadedBy = Usercode("student"),
      created = uploadFinished,
      lastUpdated = uploadFinished,
      uploadStarted = uploadStarted,
    )

}
