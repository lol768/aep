package domain.dao

import java.time.ZonedDateTime
import java.util.UUID

import domain.Assessment.State
import domain._
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class AssessmentDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  private val dao = get[AssessmentDao]
  private def lookupException = throw new Exception("DAO lookup failed")

  "AssessmentDao" should {
    "save and retrieve an assessment" in {
      val now = ZonedDateTime.of(2019, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant

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
      val now = ZonedDateTime.of(2019, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant

      DateTimeUtils.useMockDateTime(now, () => {
        val id = UUID.randomUUID()
        val ass = Fixtures.assessments.storedAssessment(id)

        val test = for {
          inserted <- dao.insert(ass)

          // java.lang.Exception: Optimistic locking failed - tried to update version 2019-01-01T10:01Z but current value is 2019-01-01T10:00Z for class class domain.dao.AssessmentsTables$StoredAssessment
          result <- dao.update(inserted.copy(title = "why is this not fine", version = inserted.version.plusMinutes(1)))

          // org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "pk_assessment_version"
          otherResult <- dao.update(inserted.copy(title = "why is this not fine"))

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

    "getToday/InWindow" in {
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

      val inWindow = firstStart.get.withHour(12).toInstant
      DateTimeUtils.useMockDateTime(inWindow, () => {
        val result = execWithCommit(dao.getInWindow)
        result.length mustEqual 1
        result.head.id mustEqual first.id
      })

      val notInWindow = firstStart.get.withHour(1).toInstant
      DateTimeUtils.useMockDateTime(notInWindow, () => {
        val result = execWithCommit(dao.getInWindow)
        result.length mustEqual 0
      })
    }
  }
}
