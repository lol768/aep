package domain.dao

import java.time.LocalDateTime
import java.util.UUID

import domain.Fixtures
import domain.Fixtures.{announcements, assessments}
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.Future
import scala.util.Random

class AnnouncementDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  import helpers.DateConversion._

  private val assDao = get[AssessmentDao]
  private val dao = get[AnnouncementDao]

  "AnnouncementDao" should {
    val now = LocalDateTime.of(2019, 1, 1, 10, 0, 0, 0).asInstant

    "save and retrieve an announcement" in {
      DateTimeUtils.useMockDateTime(now, () => {
        val ass = assessments.storedAssessment()
        val usercode = Usercode("staff1")
        val ann = announcements.storedAnnouncement(ass.id, usercode)

        val test = for {
          _ <- assDao.insert(ass)
          result <- dao.insert(ann)
          existsAfter <- dao.getById(ann.id)
          _ <- DBIO.from(Future.successful {
            result.created.toInstant mustBe ann.created.toInstant
            result.version.toInstant mustBe now
            result.assessmentId mustBe ass.id
            result.text mustBe ann.text
            result.sender mustBe ann.sender

            existsAfter must contain(result)
          })
        } yield result

        exec(test)
      })
    }

    "fetch all, and delete an announcement" in {
      DateTimeUtils.useMockDateTime(now, () => {
        val ass = assessments.storedAssessment()
        val anns = (1 to 10).map(_ => Fixtures.announcements.storedAnnouncement(ass.id, Usercode("staff1")))
        val firstId = anns(0).id

        execWithCommit(assDao.insert(ass) andThen DBIO.sequence(anns.map(dao.insert)))

        val afterInsert = execWithCommit(dao.all)
        afterInsert.length mustEqual 10
        afterInsert.count(_.assessmentId == ass.id) mustEqual 10
        afterInsert.map(_.id).distinct.length mustEqual 10

        val afterDelete = execWithCommit(dao.delete(firstId) andThen dao.all)
        afterDelete.length mustEqual 9
        afterDelete.map(_.id).distinct.length mustEqual 9
        afterDelete.count(_.id == firstId) mustEqual 0
      })
    }

    "getByAssessmentId" in {
      DateTimeUtils.useMockDateTime(now, () => {
        val ass1 = assessments.storedAssessment()
        val ass2 = assessments.storedAssessment()

        val anns = (1 to 10).map(i => {
          val assId = if (i < 6) ass1.id else ass2.id
          Fixtures.announcements.storedAnnouncement(assId, Usercode("staff1"))
        })

        execWithCommit(assDao.insert(ass1) andThen assDao.insert(ass2) andThen DBIO.sequence(anns.map(dao.insert)))

        val assIdResult = execWithCommit(dao.getByAssessmentId(ass1.id))
        assIdResult.length mustEqual 5
        assIdResult.map(_.id).distinct.size mustEqual 5

        val noAssIdResult = execWithCommit(dao.getByAssessmentId(UUID.randomUUID()))
        noAssIdResult.isEmpty mustBe true
      })
    }
  }
}
