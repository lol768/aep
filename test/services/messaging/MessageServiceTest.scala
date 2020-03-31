package services.messaging

import java.util.UUID

import domain.dao.AbstractDaoTest
import domain.messaging.MessageSender._
import domain.messaging.{MessageSave, MessageSender}
import helpers.CleanUpDatabaseAfterEachTest
import warwick.sso.UniversityID

class MessageServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  val service = get[MessageService]
  val client = UniversityID("1234567")

  "send" should {
    "reject a message to client" in {
      val sr = service.send(MessageSave("Hello", Team), client, UUID.randomUUID).futureValue
      sr.isLeft mustBe true
    }

    "store a message to team" in {
      val message = service.send(MessageSave("Hello", Client), client, UUID.randomUUID).serviceValue
      val fetched = service.findById(message.id).futureValue.value
      fetched.text mustBe "Hello"
    }
  }

}
