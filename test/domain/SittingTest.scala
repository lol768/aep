package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.{Brief, DurationStyle, Platform, uploadProcessDuration}
import domain.BaseSitting.ProgressState._
import domain.BaseSitting.SubmissionState
import helpers.SimpleSemanticRelativeTime
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile
import warwick.sso.{UniversityID, Usercode}

import scala.language.{implicitConversions, postfixOps}

class SittingTest extends PlaySpec with MockitoSugar with SimpleSemanticRelativeTime {

  private val ThreeHours = Some(Duration.ofHours(3L))
  private val Now = Some(now)
  private val NoLateAllowance = Duration.ZERO
  private val TwoHourLateAllowance = Duration.ofHours(2L)

  private case class Fixture (
    assessmentStart: Option[OffsetDateTime] = None,
    assessmentDuration: Option[Duration] = None,
    studentStart: Option[OffsetDateTime] = None,
    extraTimeAdjustmentPerHour: Option[Duration] = None,
    finaliseTime: Option[OffsetDateTime] = None,
    uploadedFiles: Seq[UploadedFile] = Seq.empty,
    durationStyle: DurationStyle = DurationStyle.DayWindow,
  ) {

    val assessment: Assessment = Assessment(
      id = UUID.randomUUID(),
      paperCode = "heron-1",
      section = None,
      title = "Exposing the threats posed to civilisation by Ardea cinerea",
      startTime = assessmentStart,
      duration = assessmentDuration,
      platform = Set(Platform.OnlineExams),
      durationStyle = Some(durationStyle),
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

    val studentAssessmentWithExtraTime = studentAssessment.copy(
      extraTimeAdjustmentPerHour = Some(Duration.ofMinutes(10L))
    )

    val declarations: Declarations = Declarations(studentAssessment.id)

    val sitting: Sitting = Sitting(
      studentAssessment,
      assessment,
      declarations,
    )

    val sittingWithExtraTime: Sitting = Sitting(
      studentAssessmentWithExtraTime,
      assessment,
      declarations,
    )
  }


  "Sitting#getProgressState" should {

    "Show as not yet open before the start date" in new Fixture (
      assessmentStart = Some(24.hours fromNow),
    ) {
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(AssessmentNotYetOpen)
      sitting.getProgressState(NoLateAllowance) mustBe Some(AssessmentNotYetOpen)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(AssessmentNotYetOpen)
    }

    "Show the generic Started state for inProgress assessments without a duration" in new Fixture (
      assessmentStart = Some(1.hour ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(30.minutes ago)
    ) {
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(InProgress)
      sitting.getProgressState(NoLateAllowance) mustBe Some(InProgress)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(InProgress)
    }

    "Respect the 24 hour window when deciding InProgress-fulness" in new Fixture (
      assessmentStart = Some(12.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(30.minutes ago)
    ) {
      // Should be in progress - 3 hr assessment started 11 1/2 hrs into 24 hr window
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(InProgress)
      sitting.getProgressState(NoLateAllowance) mustBe Some(InProgress)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(InProgress)
    }

    "Respect fixed-start assessments when deciding InProgress-fulness" in new Fixture (
      assessmentStart = Some(2.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(30.minutes ago),
      durationStyle = DurationStyle.FixedStart
    ) {
      // Should be in progress - 3 hr fixed start assessment kicked off 1 1/2 hours ago
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(InProgress)
      sitting.getProgressState(NoLateAllowance) mustBe Some(InProgress)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(InProgress)
    }

    "Show InProgress assessments" in new Fixture (
      assessmentStart = Some(1.hour ago),
      studentStart = Some(30.minutes ago)
    ) {
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(Started)
      sitting.getProgressState(NoLateAllowance) mustBe Some(Started)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(Started)
    }

    "Show as GracePeriod just after the main exam writing period" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(3.hours and 20.minutes ago)
    ) {
      // They had 3h+45m to submit, 3h+20m is in the grace upload period
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(OnGracePeriod)
      sitting.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
      // The extra time student has 4h+15m total, so 3h+20m is still InProgress
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(InProgress)
    }

    "Show as GracePeriod after main exam writing period taking into account hourly extra time" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(3.hours and 50.minutes ago)
    ) {
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
    }

    "Respect the 24 hour window when deciding GracePeriod-fulness" in new Fixture (
      assessmentStart = Some(12.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(3.hours and 20.minutes ago)
    ) {
      // Grace period should be calculated relative to when the student started, not the assessment
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(OnGracePeriod)
      sitting.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
      // Extra time means this student is still in progress
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(InProgress)
    }

    "Respect the 24 hour window when deciding GracePeriod-fulness for student with extra time agreed" in new Fixture(
      assessmentStart = Some(12.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(3.hours and 50.minutes ago)
    ) {
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
    }

    "Respect fixed-start assessments when deciding GracePeriod-fulness" in new Fixture (
      assessmentStart = Some(3.hours and 10.minutes ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(15.minutes ago),
      durationStyle = DurationStyle.FixedStart
    ) {
      // Should be in grace period regardless of student start time
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(OnGracePeriod)
      sitting.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
      // Extra time means this student should still show as in progress
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(InProgress)
    }

    "Respect fixed-start assessments when deciding GracePeriod-fulness for students with extra time agreed" in new Fixture(
      assessmentStart = Some(3.hours and 35.minutes ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(15.minutes ago),
      durationStyle = DurationStyle.FixedStart
    ) {
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
    }

    "Not have a grace or late period if assessment is started too late into the window" in new Fixture (
      assessmentStart = Some(25.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(2.hours ago)
    ) {
      // Student started with 1 hr of 24 hr window to go, and is now 10 mins past the window
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(DeadlineMissed)
      sitting.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
    }

    "Show as Late after the on time duration if late period allowance is active" in new Fixture (
      assessmentStart =  Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(4.hours ago)
    ) {
      // They had 3h+45m to submit, so 4h is in the "Late" section
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(Late)
      // If there is no late period allowance it should just be DeadlineMissed
      sitting.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
      // The extra 30 minutes for this student means they should show as OnGracePeriod
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
    }

    "Respect fixed-time assessments when deciding Late-ness if late time allowance is active" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(10.minutes ago),
      durationStyle= DurationStyle.FixedStart
    ) {
      // Doesn't matter that they only started 10 minutes ago - this is a fixed-time assessment
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(Late)
      // No late allowance = DeadlineMissed
      sitting.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
      // Extra time means this student will still be OnGracePeriod
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(OnGracePeriod)
    }

    "Show DeadlineMissed for late fixed-time assessments if extra time isn't long enough" in new Fixture (
      assessmentStart = Some(4.hours and 40.minutes ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(10.minutes ago),
      durationStyle= DurationStyle.FixedStart
    ) {
      // Doesn't matter that they only started 10 minutes ago - this is a fixed-time assessment
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
    }

    "Show as DeadlineMissed after even more time and late time allowance is active" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(6.hours ago)
    ) {
      // They had 3h+45m+2h to submit late, so 6h is right out
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(DeadlineMissed)
    }

    "Respect fixed-time assessments when deciding DeadlineMissed-ness" in new Fixture (
      assessmentStart = Some(7.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(1.hour ago),
      durationStyle = DurationStyle.FixedStart
    ) {
      // Doesn't matter when they started - absolute deadline missed
      sitting.getProgressState(TwoHourLateAllowance) mustBe Some(DeadlineMissed)
      sitting.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
      sittingWithExtraTime.getProgressState(NoLateAllowance) mustBe Some(DeadlineMissed)
    }

  }

  "Sitting#getSubmissionState" should {
    "Be None if there are no files" in new Fixture (
      assessmentStart = Some(1.hour ago),
      studentStart = Some(30.minutes ago)
    ) {
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.None
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.None
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.None
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
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.OnTime
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
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
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.Late
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.Late
      // The extra time should mean this student is still in the grace period -> OnTime
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
    }

    "Be Late if there are late files taking into account extra time" in new Fixture (
      assessmentStart = Some(1.hour ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(4.hours and 35.minutes ago),
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
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.Late
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
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.OnTime
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
    }

    "Respect fixed-time assessments when deciding OnTime-ness" in new Fixture (
      assessmentStart = Some(2.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(2.hours and 10.minutes ago),
      durationStyle = DurationStyle.FixedStart,
      uploadedFiles = Seq(
        uploadedFile( // on time
          uploadStarted = 2.hours ago,
          uploadFinished = 2.hours and 10.minutes ago // ignore
        )
      )
    ) {
      // Submission happened within the absolute OnTime period for this assessment
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.OnTime
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
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
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.Late
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.Late
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.Late
    }

    "Respect fixed-time assessments when deciding Late-ness" in new Fixture (
      assessmentStart = Some(4.hours ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(4.hours and 2.minutes ago),
      durationStyle = DurationStyle.FixedStart,
      uploadedFiles = Seq(
        uploadedFile(
          uploadStarted = 2.minutes ago,
          uploadFinished = 1.minute ago,
        )
      )
    ) {
      // Student has exceeded the absolute grace period, but not the absolute late period
      sitting.getSubmissionState(TwoHourLateAllowance) mustBe SubmissionState.Late
      sitting.getSubmissionState(NoLateAllowance) mustBe SubmissionState.Late
      // The extra time puts the upload within the grace period -> OnTime
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.OnTime
    }

    "Respect fixed-time assessments when deciding Late-ness taking into account extra time" in new Fixture (
      assessmentStart = Some(4.hours and 35.minutes ago),
      assessmentDuration = ThreeHours,
      studentStart = Some(4.hours and 2.minutes ago),
      durationStyle = DurationStyle.FixedStart,
      uploadedFiles = Seq(
        uploadedFile(
          uploadStarted = 2.minutes ago,
          uploadFinished = 1.minute ago,
        )
      )
    ) {
      sittingWithExtraTime.getSubmissionState(NoLateAllowance) mustBe SubmissionState.Late
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
