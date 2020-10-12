package domain.dao

import java.time.{Clock, Duration, LocalDateTime}
import java.util.UUID

import domain.Assessment.Platform.OnlineExams
import domain.Assessment.{DurationStyle, State}
import domain.Fixtures.studentAssessments
import domain._
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class AssessmentDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  import helpers.DateConversion._

  private val dao = get[AssessmentDao]
  private val studentDao = get[StudentAssessmentDao]
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
            result.paperCode mustBe ass.paperCode
            result.section mustBe ass.section
            result.title mustBe ass.title
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
          result.paperCode mustBe ass.paperCode
          result.section mustBe ass.section
          result.title mustBe "is this fine yet"
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
      result.map(_.paperCode).sorted mustEqual assessments.map(_.paperCode).sorted
    }

    "find by states" in {
      val draft = Fixtures.assessments.storedAssessment().copy(state = State.Draft)
      val approved = Fixtures.assessments.storedAssessment().copy(state = State.Approved)

      execWithCommit(DBIO.sequence(Seq(draft, approved).map(dao.insert)))

      val draftResult = execWithCommit(dao.findByStates(Seq(State.Draft)))
      draftResult.map(_.id) must contain only draft.id

      val draftAndApprovedResult = execWithCommit(dao.findByStates(Seq(State.Draft, State.Approved)))
      draftAndApprovedResult.map(_.id) must contain allOf (draft.id, approved.id)

      val approvedResult = execWithCommit(dao.findByStates(Seq(State.Approved)))
      approvedResult.map(_.id) must contain only approved.id
    }

    "getById/Code" in {
      val assessments = (1 to 5).map(_ => Fixtures.assessments.storedAssessment())
      val first = assessments.head
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val idResult = execWithCommit(dao.getById(first.id)).getOrElse(lookupException)
      idResult.paperCode mustEqual first.paperCode

      val codeResult = execWithCommit(dao.getByPaper(first.paperCode, first.section, first.examProfileCode)).getOrElse(lookupException)
      codeResult.id mustEqual first.id

      val noIdResult = execWithCommit(dao.getById(UUID.randomUUID()))
      noIdResult.isEmpty mustBe true

      val noCodeResult = execWithCommit(dao.getByPaper("nonexistent-code", None, "EXSUM20"))
      noCodeResult.isEmpty mustBe true
    }

    "getByIds" in {
      val assessments = (1 to 5).map(_ => Fixtures.assessments.storedAssessment())
      val notFirst = assessments.tail
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val result = execWithCommit(dao.getByIds(notFirst.map(_.id)))
      result.length mustEqual notFirst.length
      result.map(_.paperCode) mustEqual notFirst.map(_.paperCode)
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

    "find past assessments with files not sent to Tabula" in {
      val pastAssessments = (1 to 3).map(_ => Fixtures.assessments.storedAssessment(platformOption = Some(OnlineExams)))
      val futureAssessment = Fixtures.assessments.storedAssessment(platformOption = Some(OnlineExams)).copy(startTime =  pastAssessments.head.startTime.map(_.plusDays(2)))
      val assessments = pastAssessments :+ futureAssessment

      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))

      val unSubmitted = studentAssessments.storedStudentAssessment(pastAssessments(0).id).copy(tabulaSubmissionId = None, uploadedFiles = List(UUID.randomUUID()))
      val unSubmittedNoFile = studentAssessments.storedStudentAssessment(pastAssessments(1).id).copy(tabulaSubmissionId = None)
      val submitted = studentAssessments.storedStudentAssessment(pastAssessments(2).id).copy(tabulaSubmissionId = Some(UUID.randomUUID()), uploadedFiles = List(UUID.randomUUID()))

      execWithCommit(DBIO.sequence(Seq(submitted, unSubmittedNoFile, unSubmitted).map(studentDao.insert)))

      val uploadTime = pastAssessments.head.startTime.get.plusHours(25).plusMinutes(1)
      DateTimeUtils.useMockDateTime(uploadTime, () => {
        val result = exec(dao.getAssessmentsRequiringUpload)
        result.length mustEqual 1
        result.head.id mustEqual pastAssessments.head.id
      })
    }

    "find fixed-start assessments after they have finished" in {
      val dayWindowAssessment = Fixtures.assessments.storedAssessment(platformOption = Some(OnlineExams))
      val fixedStartAssessment = Fixtures.assessments.storedAssessment(platformOption = Some(OnlineExams)).copy(
        startTime = dayWindowAssessment.startTime,
        duration = Some(Duration.ofHours(2)),
        durationStyle = DurationStyle.FixedStart
      )

      val assessments = Seq(dayWindowAssessment, fixedStartAssessment)
      execWithCommit(DBIO.sequence(assessments.map(dao.insert)))
      // Give them all unsubmitted files - we're just interested in timings in this test
      assessments.foreach { a =>
        val unsubmitted = studentAssessments.storedStudentAssessment(a.id).copy(tabulaSubmissionId = None, uploadedFiles = List(UUID.randomUUID()))
        unsubmitted.extraTimeAdjustmentPerHour mustBe None
        execWithCommit(studentDao.insert(unsubmitted))
      }

      // can submit day window after 25 hours (24 hours + 1 hour buffer)
      // can submit fixed-start after 5h 45m (2h exam + 45m grace + 2h lateness + 1 hour buffer)
      val now = dayWindowAssessment.startTime.value

      withClue("Neither are available at start time") {
        DateTimeUtils.useMockDateTime(now, () => {
          val result = exec(dao.getAssessmentsRequiringUpload)
          result must have size 0
        })
      }

      withClue("Still not available after 5h 30m") {
        DateTimeUtils.useMockDateTime(now.plus(Duration.ofHours(5).plusMinutes(30)), () => {
          val result = exec(dao.getAssessmentsRequiringUpload)
          result must have size 0
        })
      }

      withClue("Fixed-start available after 5h 46m") {
        DateTimeUtils.useMockDateTime(now.plus(Duration.ofHours(5).plusMinutes(46)), () => {
          val result = exec(dao.getAssessmentsRequiringUpload)
          result must have size 1
          result.head.id mustBe fixedStartAssessment.id
        })
      }

      withClue("Both available after 25h m1") {
        DateTimeUtils.useMockDateTime(now.plus(Duration.ofHours(25).plusMinutes(1)), () => {
          val result = exec(dao.getAssessmentsRequiringUpload)
          result must have size 2
        })
      }
    }
  }
}
