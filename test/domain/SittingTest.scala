package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.{AssessmentType, Platform, State}
import domain.BaseSitting.ProgressState._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import warwick.core.helpers.JavaTime
import warwick.sso.UniversityID

class SittingTest extends PlaySpec with MockitoSugar {

  private case class Fixture (
    assessmentStart: Option[OffsetDateTime] = None,
    assessmentDuration: Option[Duration] = None,
    studentStart: Option[OffsetDateTime] = None,
    extraTimeAdjustment: Option[Duration] = None,
    finaliseTime: Option[OffsetDateTime] = None,
  ) {

    // val mockMailerClient: MailerClient = mock[MailerClient](RETURNS_SMART_NULLS)

    val assessment: AssessmentMetadata = AssessmentMetadata(
      id = UUID.randomUUID(),
      paperCode = "heron-1",
      section = None,
      title = "Exposing the threats posed to civilisation by Ardea cinerea",
      startTime = assessmentStart,
      duration = assessmentDuration,
      platform = Set(Platform.OnlineExams),
      assessmentType = Some(AssessmentType.Bespoke),
      state = State.Approved,
      tabulaAssessmentId = Some(UUID.randomUUID()),
      examProfileCode= "MAY2020",
      moduleCode = "IN-101",
      departmentCode = DepartmentCode("IN"),
      sequence = "E01",
    )

    val studentAssessment: StudentAssessmentMetadata = StudentAssessmentMetadata (
      assessmentId = UUID.randomUUID(),
      studentAssessmentId = UUID.randomUUID(),
      studentId = UniversityID("1431777"),
      inSeat = true,
      startTime = studentStart,
      extraTimeAdjustment = None,
      finaliseTime = None,
      uploadedFileCount = 0
    )


    val sitting: SittingMetadata = SittingMetadata(
      studentAssessment: StudentAssessmentMetadata,
      assessment: AssessmentMetadata
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
      studentStart = Some(JavaTime.offsetDateTime.minusMinutes(30))
    ) {
      sitting.getProgressState mustBe Some(Started)
    }
  }

}
