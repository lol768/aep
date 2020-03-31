package services.messaging

import java.util.UUID

import domain.dao.AbstractDaoTest
import domain.messaging.MessageSender._
import domain.messaging.{Message, MessageSave, MessageSender}
import helpers.CleanUpDatabaseAfterEachTest
import warwick.core.helpers.ServiceResults.ServiceResultException
import warwick.sso.UniversityID

class MessageServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  val service = get[MessageService]
  val client1 = UniversityID("1234567")
  val client2 = UniversityID("1234568")

  "send" should {
    "reject a message to client" in {
      val sr = service.send(MessageSave("Hello", Team), client1, UUID.randomUUID).futureValue
      sr.isLeft mustBe true
    }

    "store a message to team" in {
      val message = sendMessageForAssessment(UUID.randomUUID, client1)
      val fetched = service.findById(message.id).futureValue.value
      fetched.text mustBe "Hello"
    }

    "fetch message by student/assessment" in {
      val a1 = UUID.randomUUID()
      val a2 = UUID.randomUUID()

      val m1 = sendMessageForAssessment(a1, client1)
      val m2 = sendMessageForAssessment(a1, client1)
      val m3 = sendMessageForAssessment(a1, client2)
      val m4 = sendMessageForAssessment(a2, client2)

      service.findByAssessment(a1).futureValue mustBe Seq(m1,m2,m3)
      service.findByAssessment(a2).futureValue mustBe Seq(m4)

      service.findByStudentAssessment(a1, client1).futureValue mustBe Seq(m1, m2)
      service.findByStudentAssessment(a1, client2).futureValue mustBe Seq(m3)
      service.findByStudentAssessment(a2, client2).futureValue mustBe Seq(m4)
    }
  }


  private def sendMessageForAssessment(assessmentId: UUID, client: UniversityID): Message =
    service.send(MessageSave("Hello", Client), client, assessmentId).serviceValue
}
