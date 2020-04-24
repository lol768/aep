package services

import java.time.{LocalDate, LocalTime, OffsetTime, ZoneOffset}
import java.util.concurrent.CompletableFuture

import domain._
import domain.dao.AbstractDaoTest
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => isEq, _}
import org.mockito.Mockito._
import org.quartz.Scheduler
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject._
import system.BindingOverrides
import uk.ac.warwick.util.core.DateTimeUtils
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import uk.ac.warwick.util.mywarwick.model.response.Response
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.compat.java8.FutureConverters
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class NotificationServiceTest
  extends AbstractDaoTest
    with NoAuditLogging
    with BeforeAndAfterEach {

  private lazy val mockMyWarwickService = mock[MyWarwickService]
  private lazy val mockScheduler = mock[Scheduler]

  private lazy val service = get[NotificationService]

  implicit override def fakeApplication: Application = fakeApplicationBuilder()
    .overrides(
      bind[MyWarwickService].toInstance(mockMyWarwickService),
      bind[Scheduler].toInstance(mockScheduler)
    )
    .build()

  override def afterEach(): Unit = {
    super.afterEach()
    // need to reset the mocked services to ready them for the next test
    reset(mockMyWarwickService)
  }

  private trait BaseFixture {
    lazy val activityCaptor: ArgumentCaptor[Activity] = ArgumentCaptor.forClass(classOf[Activity])

    when(mockMyWarwickService.sendAsNotification(any())).thenReturn(cfSuccess(List(new Response()).asJava))
  }

  private class AssessmentsWithStudentsFixture extends BaseFixture {
    lazy val assessmentService: AssessmentService = get[AssessmentService]
    lazy val studentAssessmentService: StudentAssessmentService = get[StudentAssessmentService]

    val assessment: Assessment = assessmentService.insert(Fixtures.assessments.storedAssessment().asAssessment(Map.empty), Seq.empty).serviceValue
    val studentAssessments: Set[StudentAssessment] = studentAssessmentService.insert(Set(
      Fixtures.studentAssessments.storedStudentAssessment(assessment.id, Fixtures.users.student1.universityId.get).copy(startTime = Some(JavaTime.offsetDateTime)).asStudentAssessment(Map.empty),
      Fixtures.studentAssessments.storedStudentAssessment(assessment.id, Fixtures.users.student2.universityId.get).copy(startTime = Some(JavaTime.offsetDateTime)).asStudentAssessment(Map.empty),
      Fixtures.studentAssessments.storedStudentAssessment(assessment.id, Fixtures.users.student3.universityId.get).copy(startTime = Some(JavaTime.offsetDateTime)).asStudentAssessment(Map.empty),
    )).serviceValue
  }

  private class AnnouncementFixture extends AssessmentsWithStudentsFixture {
    val announcement: Announcement = Fixtures.announcements.storedAnnouncement(assessment.id, Usercode("staff1")).asAnnouncement
  }

  "NotificationService" should {
    "notify students of a new announcement to an assessment they are sitting" in new AnnouncementFixture {
      service.newAnnouncement(announcement).serviceValue

      verify(mockMyWarwickService).queueNotification(
        activityCaptor.capture(),
        isEq(mockScheduler)
      )

      val activity: Activity = activityCaptor.getValue
      activity.getRecipients.getUsers.asScala mustBe Set(Fixtures.users.student1.usercode.string, Fixtures.users.student2.usercode.string, Fixtures.users.student3.usercode.string)
      activity.getTitle mustBe "CY5637 Information theory: Testing Adventurous Promiscuities and Fury: Announcement"
      activity.getUrl mustBe s"https://example.warwick.ac.uk/assessment/${assessment.id}"
      activity.getText mustBe "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Lut enim ad minim veniam, quis nostrud exercitation ullamco"
      activity.getType mustBe "assessment-announcement"
    }

    "send a reminder of assessments starting today" in new AssessmentsWithStudentsFixture {
      DateTimeUtils.useMockDateTime(LocalDate.of(2018, 1, 1).atTime(OffsetTime.of(LocalTime.of(8, 0), ZoneOffset.ofHours(0))), () => {
        service.sendReminders(assessment).serviceValue

        verify(mockMyWarwickService).queueNotification(
          activityCaptor.capture(),
          isEq(mockScheduler)
        )

        val activity: Activity = activityCaptor.getValue
        activity.getRecipients.getUsers.asScala mustBe Set(Fixtures.users.student1.usercode.string, Fixtures.users.student2.usercode.string, Fixtures.users.student3.usercode.string)
        activity.getTitle mustBe "CY5637: Your alternative assessment for 'Information theory: Testing Adventurous Promiscuities and Fury' is due today"
        activity.getUrl mustBe s"https://example.warwick.ac.uk/assessment/${assessment.id}"
        activity.getText mustBe "You can start this assessment between 10:00 today and 10:00, Tue 2nd Jan (GMT)."
        activity.getType mustBe "assessment-reminder"
      })
    }
  }

  private def cfSuccess[A](a: A): CompletableFuture[A] = FutureConverters.toJava(Future.successful(a)).toCompletableFuture
}
