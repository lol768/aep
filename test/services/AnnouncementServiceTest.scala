package services

import java.util.UUID

import actors.WebSocketActor.AssessmentAnnouncement
import controllers.WebSocketController.Topics
import domain.dao.AbstractDaoTest
import domain.{Announcement, Fixtures}
import helpers.CleanUpDatabaseAfterEachTest
import org.mockito.Mockito._
import play.api.Application
import play.api.inject._
import warwick.core.helpers.JavaTime

class AnnouncementServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  private lazy val mockPubSubService: PubSubService = mock[PubSubService]
  private lazy val service = get[AnnouncementService]

  implicit override def fakeApplication: Application = fakeApplicationBuilder().overrides(
      bind[PubSubService].toInstance(mockPubSubService),
    )
    .build()

  "save" should {
    "publish correct versions of announcement to students and invigilators" in {
      val announcement = Announcement(
        id = UUID.randomUUID(),
        sender = Some(Fixtures.users.staff1.usercode),
        assessment = UUID.randomUUID(),
        text = "Hello this is some text",
        created = JavaTime.offsetDateTime
      )

      service.save(announcement)
      // students
      verify(mockPubSubService).publish(Topics.allStudentsAssessment(announcement.assessment),
        AssessmentAnnouncement.from(announcement))

      // invigilators
      verify(mockPubSubService).publish(Topics.allInvigilatorsAssessment(announcement.assessment),
        AssessmentAnnouncement(announcement.id.toString, announcement.assessment.toString, Fixtures.users.staff1.name.full, announcement.text, announcement.created))
    }
  }
}
