package services

import java.util.UUID

import actors.WebSocketActor.AssessmentAnnouncement
import domain.{Announcement, Fixtures}
import domain.dao.AbstractDaoTest
import helpers.CleanUpDatabaseAfterEachTest
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
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
      verify(mockPubSubService).publish(s"studentAssessment:${announcement.assessment}",
        AssessmentAnnouncement(announcement.text, announcement.created))

      // invigilators
      verify(mockPubSubService).publish(s"invigilatorAssessment:${announcement.assessment}",
        AssessmentAnnouncement(s"${Fixtures.users.staff1.name.full.get}: ${announcement.text}", announcement.created))
    }
  }
}
