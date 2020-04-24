package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.{AssessmentType, Brief, DurationStyle, Platform}
import domain.BaseSitting.ProgressState._
import domain.BaseSitting.SubmissionState
import helpers.SimpleSemanticRelativeTime
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}
import scala.language.implicitConversions

class SittingTest extends PlaySpec with MockitoSugar with SimpleSemanticRelativeTime {

  private case class Fixture (
    assessmentStart: Option[OffsetDateTime] = None,
    assessmentDuration: Option[Duration] = None,
    studentStart: Option[OffsetDateTime] = None,
    extraTimeAdjustmentPerHour: Option[Duration] = None,
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
      durationStyle = DurationStyle.DayWindow,
      state = Assessment.State.Approved,
      tabulaAssessmentId = Some(UUID.randomUUID()),
      tabulaAssignments = Set.empty,
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
      occurrence = None,
      academicYear = None,
      inSeat = true,
      startTime = studentStart,
      extraTimeAdjustmentPerHour = extraTimeAdjustmentPerHour,
      explicitFinaliseTime = None,
      uploadedFiles = uploadedFiles,
      tabulaSubmissionId = None
    )

    val declarations: Declarations = Declarations(studentAssessment.id)

    val sitting: Sitting = Sitting(
      studentAssessment,
      assessment,
      declarations
    )
  }

  private val ThreeHours = Some(Duration.ofHours(3L))
  private val Now = Some(now)

  "Sitting#getProgressState" should {

    "Show as not yet open before the start date" in new Fixture (
      assessmentStart = Some(24.hours fromNow),
    ) {
      sitting.getProgressState mustBe Some(AssessmentNotYetOpen)
    }

    "Show the generic Started state for inProgress assessments without a duration" in new Fixture (
      assessmentStart = Some(1.hour ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(30.minutes ago)
    ) {
      sitting.getProgressState mustBe Some(InProgress)
    }

    "Respect the 24 hour window when deciding InProgress-fulness" in new Fixture (
      assessmentStart = Some(12.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(30.minutes ago)
    ) {
      // Should be in progress - 3 hr assessment started 11 1/2 hrs into 24 hr window
      sitting.getProgressState mustBe Some(InProgress)
    }

    "Show InProgress assessments" in new Fixture (
      assessmentStart = Some(1.hour ago),
      studentStart = Some(30.minutes ago)
    ) {
      sitting.getProgressState mustBe Some(Started)
    }

    "Show as GracePeriod just after the main exam writing period" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(3.hours and 10.minutes ago)
    ) {
      // They had 3h+45m to submit, 3h+10m is in the grace upload period
      sitting.getProgressState mustBe Some(OnGracePeriod)
    }

    "Resepct the 24 hour window when deciding GracePeriod-fulness" in new Fixture (
      assessmentStart = Some(12.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(3.hours and 10.minutes ago)
    ) {
      // Grace period should be calculated relative to when the student started, not the assessment
      sitting.getProgressState mustBe Some(OnGracePeriod)
    }

    "Not have a grace or late period if assessment is started too late into the window" in new Fixture (
      assessmentStart = Some(25.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(2.hours ago)
    ) {
      // Student started with 1 hr of 24 hr window to go, and is now 10 mins past the window
      sitting.getProgressState mustBe Some(DeadlineMissed)
    }

    "Show as Late after the on time duration" in new Fixture (
      assessmentStart =  Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(4.hours ago)
    ) {
      // They had 3h+45m to submit, so 4h is in the "Late" section
      sitting.getProgressState mustBe Some(Late)
    }

    "Show as DeadlineMissed after even more time" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(6.hours ago)
    ) {
      // They had 3h+45m+2h to submit late, so 6h is right out
      sitting.getProgressState mustBe Some(DeadlineMissed)
    }

  }

  "Sitting#getSubmissionState" should {
    "Be None if there are no files" in new Fixture (
      assessmentStart = Some(1.hour ago),
      studentStart = Some(30.minutes ago)
    ) {
      sitting.getSubmissionState mustBe SubmissionState.None
    }

    "Be OnTime if all files on time" in new Fixture (
      assessmentStart = Some((1 hour) ago),
      assessmentDuration = ThreeHours,
      studentStart = Some((4 hours) ago),
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
      assessmentStart = Some(1.hour ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(4.hours ago),
      uploadedFiles = Seq(
        uploadedFile( // on time
          uploadStarted = JavaTime.offsetDateTime.minusHours(3),
          uploadFinished = JavaTime.offsetDateTime.minusMinutes(10) // ignore
        ),
        uploadedFile( // late!
          uploadStarted = 5.minutes ago,
          uploadFinished = 3.minutes ago // ignore
        )
      )
    ) {
      sitting.getSubmissionState mustBe SubmissionState.Late
    }

    "Respect the 24 hour window when deciding OnTime-ness" in new Fixture (
      assessmentStart = Some(12.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(6.hours ago),
      uploadedFiles = Seq(
        uploadedFile( // on time
          uploadStarted = 5.hours ago,
          uploadFinished = 4.hours ago // ignore
        )
      )
    ) {
      // Student starts 6 hours into the 24 hour window, submits 8 hours in, duration = 3 hours so on time
      sitting.getSubmissionState mustBe SubmissionState.OnTime
    }

    "Resepct the 24 hour window when deciding Late-ness" in new Fixture (
      assessmentStart = Some(23.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Now,
      uploadedFiles = Seq(
        uploadedFile( // on time
          uploadStarted = 2.hours fromNow,
          uploadFinished = 2.hours fromNow
        )
      )
    ) {
      // Student started with only 1 hour of window left, submitted 2 hours later, so should
      // be late because it's outside 24 hour window, even though it's inside 3 hour duration
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
