package services

import java.time.{Clock, LocalDateTime}
import java.util.UUID

import domain.Fixtures
import domain.Fixtures.{studentAssessments, users}
import domain.dao.{AbstractDaoTest, AssessmentDao, StudentAssessmentDao, UploadedFileDao}
import helpers.{CleanUpDatabaseAfterEachTest, DaoPatience}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

class ReportingServiceTest
  extends AbstractDaoTest
  with CleanUpDatabaseAfterEachTest
  with MockitoSugar
  with ScalaFutures
  with NoAuditLogging
  with DaoPatience {

  private lazy val assDao = get[AssessmentDao]
  private lazy val sittingDao = get[StudentAssessmentDao]
  private lazy val fileDao = get[UploadedFileDao]
  private lazy val service = get[ReportingService]

  private val baseTime = LocalDateTime.of(2019, 4, 20, 0, 0, 0, 0)

  private trait Fixture {
    import helpers.DateConversion._

    // 10:25am, not on the half-hour else you hit sittings with almost exactly the same end time, and flappiness ensues.
    // This time ensures it will include any sittings that might complete at 10:30am
    val now = baseTime.plusHours(10).plusMinutes(25)

    DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(baseTime.asInstant, JavaTime.timeZone)

    private val startTimes = (0L to 48L).map(h => baseTime.plusHours(h))
    private val assessments = startTimes.map(st => Fixtures.assessments.storedAssessment().copy(startTime = Some(st.asOffsetDateTime)))
    private val students = users.students(20)
    private val genSittings = students.toSeq.flatMap { s =>
      assessments.map { a =>
        studentAssessments.storedStudentAssessment(a.id, s.universityId.get)
      }
    }

    val sampleAssessment = assessments(13).id

    private val sampleAssessmentSittingIds = genSittings.filter(_.assessmentId == sampleAssessment).map(_.id)
    private val startedSittingIds = sampleAssessmentSittingIds.take(15)
    private val submittedSittingIds = startedSittingIds.take(10)
    private val finalisedSittingIds = submittedSittingIds.take(5)

    private val sittings = genSittings.map {
      case sitting if finalisedSittingIds.contains(sitting.id) =>
        sitting.copy(inSeat = false, startTime = Some(now.asOffsetDateTime), uploadedFiles = List(UUID.randomUUID()), finaliseTime = Some(now.plusHours(1).asOffsetDateTime))
      case sitting if submittedSittingIds.contains(sitting.id) =>
        sitting.copy(inSeat = true, startTime = Some(now.asOffsetDateTime), uploadedFiles = List(UUID.randomUUID()))
      case sitting if startedSittingIds.contains(sitting.id) =>
        sitting.copy(inSeat = true, startTime = Some(now.asOffsetDateTime))
      case unaltered => unaltered
    }

    println(sittings.flatMap(_.uploadedFiles))

    execWithCommit(DBIO.sequence(for {
        sitting <- sittings
        fileId <- sitting.uploadedFiles
      } yield {
        fileDao.insert(Fixtures.uploadedFiles.storedUploadedStudentAssessmentFile(sitting.id, fileId))
      }
    ))

    execWithCommit(DBIO.sequence(assessments.map(assDao.insert)) andThen DBIO.sequence(sittings.map(sittingDao.insert)))

    DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone

  }

  new Fixture {
    "ReportingService" should {
      "return expected results" in {
        DateTimeUtils.useMockDateTime(now, () => {
          val todayResults = service.todayAssessments.serviceValue
          todayResults.length mustBe 24
          // one starting at every hour, on the hour
          todayResults.map(_.startTime.value.getMinute).distinct mustEqual Seq(0)
          todayResults.map(_.startTime.value.getHour) mustEqual (0 to 23)

          // Not sure what makes sense in this section following new approach to timings (OE-116)
          val startedAndSubmittableResults = service.startedAndSubmittableAssessments.serviceValue
          startedAndSubmittableResults must have size 20
          startedAndSubmittableResults.map(_.startTime.value.getHour) mustEqual (4 to 23)

          val expectedResults = service.expectedSittings(sampleAssessment).serviceValue
          expectedResults.length mustBe 20

          val startedResults = service.startedSittings(sampleAssessment).serviceValue
          startedResults.length mustBe 15

          val submittedResults = service.submittedSittings(sampleAssessment).serviceValue
          submittedResults.length mustBe 10

          val finalisedResults = service.finalisedSittings(sampleAssessment).serviceValue
          finalisedResults.length mustBe 5
        })
      }
    }
  }
}
