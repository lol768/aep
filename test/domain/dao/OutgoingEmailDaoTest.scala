package domain.dao

import java.time.{Clock, OffsetDateTime, ZonedDateTime}

import domain._
import play.api.libs.json.Json
import play.api.libs.mailer.Email
import slick.dbio.DBIOAction
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.Future

class OutgoingEmailDaoTest extends AbstractDaoTest {

  private val dao = get[OutgoingEmailDao]

  "OutgoingEmailDao" should {
    "save an email" in {
      val now = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      DateTimeUtils.useMockDateTime(now, () => {
        val email = Email(
          subject = "Here's a lovely email",
          from = "no-reply@warwick.ac.uk",
          bodyText = Some("Love it")
        )

        val outgoingEmail = OutgoingEmail(
          email = email,
          recipient = Some(Usercode("cuscav"))
        )

        val test = for {
          result <- dao.insert(outgoingEmail)
          existsAfter <- dao.get(result.id)
          _ <- DBIO.from(Future {
            result.version.toInstant.equals(now) mustBe true

            result.parsed.created.toInstant.equals(now) mustBe true
            result.parsed.email mustBe email
            result.parsed.recipient mustBe outgoingEmail.recipient

            existsAfter.isEmpty mustBe false
            existsAfter mustBe Some(result)
          })
        } yield result

        exec(test)
      })
    }

    "update an email" in {
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )

      val outgoingEmail = OutgoingEmail(
        email = email,
        recipient = Some(Usercode("cuscav"))
      )

      val updatedOutgoingEmail = outgoingEmail.copy(
        emailAddress = Some("m.mannion@warwick.ac.uk")
      )

      val earlier = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      val now = ZonedDateTime.of(2018, 1, 1, 11, 0, 0, 0, JavaTime.timeZone).toInstant

      val test = for {
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        })
        inserted <- dao.insert(outgoingEmail)
        _ <- DBIO.from(Future {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        })
        updated <- dao.update(updatedOutgoingEmail.copy(id = Some(inserted.id)), inserted.version)
        _ <- DBIO.from(Future {
          updated.id mustBe inserted.id
          updated.version.toInstant.equals(now) mustBe true
          updated.parsed.email mustBe email
          updated.parsed.recipient mustBe outgoingEmail.recipient
          updated.parsed.emailAddress mustBe updatedOutgoingEmail.emailAddress
        })
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

    "insert multiple emails" in {
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )

      val recipients = LazyList(Usercode("cuscav"), Usercode("cusebr"), Usercode("cusfal"), Usercode("cusjau"), Usercode("cuskak"))

      val emails = recipients.map { usercode =>
        OutgoingEmail(
          id = None, // Allow the DAO to set this
          email = email,
          recipient = Some(usercode)
        )
      }

      val inserted = exec(dao.insertAll(emails))
      inserted.size mustBe recipients.size
      inserted.foreach { e =>
        e.id mustNot be(null)
        e.email mustBe Json.obj(
          "subject" -> "Here's a lovely email",
          "from" -> "no-reply@warwick.ac.uk",
          "to" -> Seq.empty[String],
          "bodyText" -> "Love it",
          "cc" -> Seq.empty[String],
          "bcc" -> Seq.empty[String],
          "replyTo" -> Seq.empty[String],
          "attachments" -> Seq.empty[String],
          "headers" -> Seq.empty[String],
        )
        recipients.contains(e.recipient.get) mustBe true
        e.emailAddress mustBe empty
        e.sent mustBe empty
        e.lastSendAttempt mustBe empty
        e.failureReason mustBe empty
      }
      recipients.forall(inserted.map(_.recipient.get).contains) mustBe true
    }
    
    
    "return the emails sent to an email address" in {
      val dateSent = ZonedDateTime.of(2020, 2, 2, 11, 0, 0, 0, JavaTime.timeZone).toOffsetDateTime
      val someDateSent = Some(dateSent)
      
      val searchDateFrom = Some(dateSent.minusDays(4))
      val searchDateTo = Some(dateSent.plusDays(4))
            
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )
      
      val outgoingEmails = Seq(
        OutgoingEmail(email = email, recipient = Some(Usercode("cusbis")), emailAddress = Some("m.biscuits@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cuschi")), emailAddress = Some("s.chips@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cusfis")), emailAddress = Some("q.fish@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cuslem")), emailAddress = Some("b.lemons@warwick.ac.uk"), sent = someDateSent),
      )
      
      val anotherEmail = Email(
        subject = "Here's a different lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Quite like it")
      )
      
      val anotherOutgoingEmail = OutgoingEmail(
        email = anotherEmail, 
        recipient = Some(Usercode("cusbis")),
        emailAddress = Some("m.biscuits@warwick.ac.uk"),
        sent = someDateSent
      )
      
      val test = for {
        _ <- dao.insertAll(outgoingEmails)
        _ <- dao.insert(anotherOutgoingEmail)
        searchResults  <- dao.getEmailsSentTo(Seq("m.biscuits@warwick.ac.uk"), searchDateFrom, searchDateTo, 0, 10)
        _ <- DBIOAction.from(Future {
          searchResults.size mustBe 2
          val recipientEmailAddresses = searchResults.map(_.emailAddress)
          recipientEmailAddresses.contains(Some("m.biscuits@warwick.ac.uk")) mustBe true
          recipientEmailAddresses.contains(Some("s.chips@warwick.ac.uk")) mustBe false
          recipientEmailAddresses.contains(Some("q.fish@warwick.ac.uk")) mustBe false
          recipientEmailAddresses.contains(Some("b.lemons@warwick.ac.uk")) mustBe false
        })
      } yield searchResults
      
      exec(test)
    }
    
    "return the emails sent between two dates" in {
      val dateSent = ZonedDateTime.of(2020, 2, 2, 11, 0, 0, 0, JavaTime.timeZone).toOffsetDateTime // 4 out of the 6 we'll pretend to send will have this date
      val olderEmailDateSent = Some(dateSent.minusDays(6)) // 2 out of the 6 will have this older date, outside of our search criteria
      val someDateSent = Some(dateSent)

      val searchDateFrom = Some(dateSent.minusDays(4))
      val searchDateTo = Some(dateSent.plusDays(4))
      
      val email = Email(
        subject = "Here's a lovely email",
        from = "no-reply@warwick.ac.uk",
        bodyText = Some("Love it")
      )

      val outgoingEmails = Seq(
        OutgoingEmail(email = email, recipient = Some(Usercode("cusbis")), emailAddress = Some("m.biscuits@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cuschi")), emailAddress = Some("s.chips@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cusfis")), emailAddress = Some("q.fish@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cuslem")), emailAddress = Some("b.lemons@warwick.ac.uk"), sent = someDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cusfen")), emailAddress = Some("b.fennel@warwick.ac.uk"), sent = olderEmailDateSent),
        OutgoingEmail(email = email, recipient = Some(Usercode("cuslov")), emailAddress = Some("b.lovage@warwick.ac.uk"), sent = olderEmailDateSent),
      )
      
      val test = for {
        _ <- dao.insertAll(outgoingEmails)
        searchResults  <- dao.getEmailsSentTo(Seq.empty, searchDateFrom, searchDateTo, 0, 10)
        _ <- DBIOAction.from(Future {
          searchResults.size mustBe 4
          val recipientEmailAddresses = searchResults.map(_.emailAddress)
          recipientEmailAddresses.contains(Some("m.biscuits@warwick.ac.uk")) mustBe true
          recipientEmailAddresses.contains(Some("s.chips@warwick.ac.uk")) mustBe true
          recipientEmailAddresses.contains(Some("q.fish@warwick.ac.uk")) mustBe true
          recipientEmailAddresses.contains(Some("b.lemons@warwick.ac.uk")) mustBe true
          recipientEmailAddresses.contains(Some("b.fennel@warwick.ac.uk")) mustBe false
          recipientEmailAddresses.contains(Some("b.lovage@warwick.ac.uk")) mustBe false
        })
      } yield searchResults

      exec(test)
    }    
  }
}
