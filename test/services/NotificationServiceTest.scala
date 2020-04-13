package services

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
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import uk.ac.warwick.util.mywarwick.model.response.Response
import warwick.core.Logging

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

  private val domain = "example.warwick.ac.uk"

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
      Fixtures.studentAssessments.storedStudentAssessment(assessment.id, Fixtures.users.student1.universityId.get).asStudentAssessment(Map.empty),
      Fixtures.studentAssessments.storedStudentAssessment(assessment.id, Fixtures.users.student2.universityId.get).asStudentAssessment(Map.empty),
      Fixtures.studentAssessments.storedStudentAssessment(assessment.id, Fixtures.users.student3.universityId.get).asStudentAssessment(Map.empty),
    )).serviceValue
  }

  private class AnnouncementFixture extends AssessmentsWithStudentsFixture {
    val announcement: Announcement = Fixtures.announcements.storedAnnouncement(assessment.id).asAnnouncement
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
  }

  private def cfSuccess[A](a: A): CompletableFuture[A] = FutureConverters.toJava(Future.successful(a)).toCompletableFuture
}
