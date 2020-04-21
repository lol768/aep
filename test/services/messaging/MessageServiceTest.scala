package services.messaging

import java.util.UUID

import domain.dao.AbstractDaoTest
import domain.messaging.MessageSender._
import domain.messaging.{Message, MessageSave}
import helpers.CleanUpDatabaseAfterEachTest
import warwick.sso.UniversityID

class MessageServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  private val service = get[MessageService]
  private val client1 = UniversityID("1234567")
  private val client2 = UniversityID("1234568")

  "send" should {
    "store a message to team" in {
      val message = sendMessageForAssessment(UUID.randomUUID, client1)
      val fetched = service.findById(message.id).serviceValue.value
      fetched.text mustBe "Hello"
    }

    "fetch message by student/assessment" in {
      val a1 = UUID.randomUUID()
      val a2 = UUID.randomUUID()

      val m1 = sendMessageForAssessment(a1, client1)
      val m2 = sendMessageForAssessment(a1, client1)
      val m3 = sendMessageForAssessment(a1, client2)
      val m4 = sendMessageForAssessment(a2, client2)

      service.findByAssessment(a1).serviceValue mustBe Seq(m1,m2,m3)
      service.findByAssessment(a2).serviceValue mustBe Seq(m4)

      service.findByStudentAssessment(a1, client1).serviceValue mustBe Seq(m1, m2)
      service.findByStudentAssessment(a1, client2).serviceValue mustBe Seq(m3)
      service.findByStudentAssessment(a2, client2).serviceValue mustBe Seq(m4)
    }
  }


  private def sendMessageForAssessment(assessmentId: UUID, client: UniversityID): Message =
    service.send(MessageSave("Hello", Student, None), client, assessmentId).serviceValue
}
