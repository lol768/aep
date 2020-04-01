package domain.dao

import java.time.{Clock, LocalDateTime}
import java.util.UUID

import domain.Assessment.State
import domain._
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class AssessmentDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  import helpers.DateConversion._

  private val dao = get[AssessmentDao]
  private def lookupException = throw new Exception("DAO lookup failed")

  "AssessmentDao" should {
    "save and retrieve an assessment" in {
      val now = LocalDateTime.of(2019, 1, 1, 10, 0, 0, 0).asInstant

      DateTimeUtils.useMockDateTime(now, () => {
        val id = UUID.randomUUID()
        val ass = Fixtures.assessments.storedAssessment(id)

        val test = for {
          result <- dao.insert(ass)
          existsAfter <- dao.getById(id)
          _ <- DBIO.from(Future.successful {
            result.created.toInstant mustBe ass.created.toInstant
            result.version.toInstant mustBe now
            result.code mustBe ass.code
            result.title mustBe ass.title
            result.assessmentType mustBe ass.assessmentType
            result.platform mustBe ass.platform
            result.startTime mustBe ass.startTime
            result.duration mustBe ass.duration

            existsAfter must contain(result)
          })
        } yield result

        exec(test)
      })
    }

    "update an assessment" in {
      val now = LocalDateTime.of(2019, 1, 1, 10, 0, 0, 0).asInstant
      val earlier = now.minusSeconds(600)

      val id = UUID.randomUUID()
      val ass = Fixtures.assessments.storedAssessment(id)

      val test = for {
        _ <- DBIO.from(Future.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        })
        inserted <- dao.insert(ass)

        _ <- DBIO.from(Future.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        })
        result <- dao.update(inserted.copy(title = "is this fine yet"))

        existsAfter <- dao.getById(id)
        _ <- DBIO.from(Future.successful {
          result.created.toInstant mustBe ass.created.toInstant
          result.version.toInstant mustBe now
          result.code mustBe ass.code
          result.title mustBe "is this fine yet"
          result.assessmentType mustBe ass.assessmentType
          result.platform mustBe ass.platform
          result.startTime mustBe ass.startTime
          result.duration mustBe ass.duration

          existsAfter must contain(result)
        })
      } yield result

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

    "fetch all" in {
      val assessments = (1 to 10).map(_ => Fixtures.assessments.storedAssessment())
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val result = execWithCommit(dao.all)
      result.length mustEqual 10
      result.map(_.code).sorted mustEqual assessments.map(_.code).sorted
    }

    "find by states" in {
      val draft = Fixtures.assessments.storedAssessment().copy(state = State.Draft)
      val submitted = Fixtures.assessments.storedAssessment().copy(state = State.Submitted)
      val approved = Fixtures.assessments.storedAssessment().copy(state = State.Approved)

      execWithCommit(DBIO.sequence(Seq(draft, submitted, approved).map(dao.insert)))

      val draftResult = execWithCommit(dao.findByStates(Seq(State.Draft)))
      draftResult.map(_.id) must contain only draft.id

      val draftAndSubmittedResult = execWithCommit(dao.findByStates(Seq(State.Draft, State.Submitted)))
      draftAndSubmittedResult.map(_.id) must contain allOf (draft.id, submitted.id)

      val approvedResult = execWithCommit(dao.findByStates(Seq(State.Approved)))
      approvedResult.map(_.id) must contain only approved.id
    }

    "getById/Code" in {
      val assessments = (1 to 5).map(_ => Fixtures.assessments.storedAssessment())
      val first = assessments.head
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val idResult = execWithCommit(dao.getById(first.id)).getOrElse(lookupException)
      idResult.code mustEqual first.code

      val codeResult = execWithCommit(dao.getByCode(first.code)).getOrElse(lookupException)
      codeResult.id mustEqual first.id

      val noIdResult = execWithCommit(dao.getById(UUID.randomUUID()))
      noIdResult.isEmpty mustBe true

      val noCodeResult = execWithCommit(dao.getByCode("nonexistent-code"))
      noCodeResult.isEmpty mustBe true
    }

    "getByIds" in {
      val assessments = (1 to 5).map(_ => Fixtures.assessments.storedAssessment())
      val notFirst = assessments.tail
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val result = execWithCommit(dao.getByIds(notFirst.map(_.id)))
      result.length mustEqual notFirst.length
      result.map(_.code) mustEqual notFirst.map(_.code)
    }

    "getToday" in {
      val generatedAssessments = (1 to 5).map(_ => Fixtures.assessments.storedAssessment())
      val first = generatedAssessments(0)
      val second = generatedAssessments(1)
      val firstStart = first.startTime.map(_.plusDays(3).withHour(9)) // offset the date, definitely in the window
      val secondStart = second.startTime.map(_.plusDays(3).withHour(23)) // offset the date, late night special
      val assessments = Seq(first.copy(startTime = firstStart), second.copy(startTime = secondStart)) ++ generatedAssessments.drop(2)
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val early = firstStart.get.withHour(0).toInstant
      DateTimeUtils.useMockDateTime(early, () => {
        val result = execWithCommit(dao.getToday)
        result.length mustEqual 2
        result.head.id mustEqual first.id
      })
    }
  }
}
