package domain.dao

import java.time.{Clock, ZonedDateTime}
import java.util.UUID

import domain.Fixtures.{assessments, studentAssessments, users}
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class StudentAssessmentDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  private val assDao = get[AssessmentDao]
  private val dao = get[StudentAssessmentDao]

  "StudentAssessmentDao" should {
    val student1 = users.student1.universityId.get
    val student2 = users.student2.universityId.get
    val now = ZonedDateTime.of(2019, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant

    "save, retrieve, and update a student assessment" in {
      val earlier = now.minusSeconds(600)

      val test = for {
        _ <- DBIO.from(Future.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        })

        assessment = assessments.storedAssessment()
        sa = studentAssessments.storedStudentAssessment(assessment.id, student1)
        _ <- assDao.insert(assessment)
        inserted <- dao.insert(sa)

        _ <- DBIO.from(Future.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        })

        _ <- DBIO.from(Future.successful {
          inserted.created.toInstant mustBe sa.created.toInstant
          inserted.version.toInstant mustBe earlier
          inserted.id mustEqual sa.id
          inserted.assessmentId mustEqual assessment.id
          inserted.studentId mustEqual student1
          inserted.startTime mustBe None
          inserted.finaliseTime mustBe None
          inserted.inSeat mustBe false
          inserted.uploadedFiles.isEmpty mustBe true
        })

        updated <- dao.update(inserted.copy(inSeat = true, startTime = Some(JavaTime.offsetDateTime)))

        _ <- DBIO.from(Future.successful {
          updated.created.toInstant mustBe sa.created.toInstant
          updated.version.toInstant mustBe now
          updated.id mustEqual sa.id
          updated.assessmentId mustEqual assessment.id
          updated.studentId mustEqual student1
          updated.startTime mustBe Some(JavaTime.offsetDateTime)
          updated.finaliseTime mustBe None
          updated.inSeat mustBe true
          updated.uploadedFiles.isEmpty mustBe true
        })
      } yield updated

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }

    "fetch all" in {
      DateTimeUtils.useMockDateTime(now, () => {
        val assessment = assessments.storedAssessment()
        val sa1 = studentAssessments.storedStudentAssessment(assessment.id, student1)
        val sa2 = studentAssessments.storedStudentAssessment(assessment.id, student2)
        execWithCommit(assDao.insert(assessment) andThen dao.insert(sa1) andThen dao.insert(sa2))

        val result = execWithCommit(dao.all)
        result.length mustEqual 2
        result.count(_.studentId == student1) mustEqual 1
      })
    }

    "getByUniversityId/AssessmentId" in {
      DateTimeUtils.useMockDateTime(now, () => {
        val assessment = assessments.storedAssessment()
        val secondAssessment = assessments.storedAssessment()

        val sa1 = studentAssessments.storedStudentAssessment(assessment.id, student1)
        val sa2 = studentAssessments.storedStudentAssessment(assessment.id, student2)
        val sa3 = studentAssessments.storedStudentAssessment(secondAssessment.id, student1)
        val sa4 = studentAssessments.storedStudentAssessment(secondAssessment.id, student2)
        val sas = Seq(sa1, sa2, sa3, sa4)

        execWithCommit(assDao.insert(assessment) andThen assDao.insert(secondAssessment) andThen DBIO.sequence(sas.map(dao.insert)))

        val uniIdResult = execWithCommit(dao.getByUniversityId(student1))
        uniIdResult.length mustEqual 2
        uniIdResult.map(_.assessmentId).distinct.size mustEqual 2

        val assIdResult = execWithCommit(dao.getByAssessmentId(assessment.id))
        assIdResult.length mustEqual 2
        assIdResult.map(_.studentId).distinct.size mustEqual 2

        val noUniIdResult = execWithCommit(dao.getByUniversityId(users.staff1.universityId.get))
        noUniIdResult.isEmpty mustBe true

        val noAssIdResult = execWithCommit(dao.getByAssessmentId(UUID.randomUUID()))
        noAssIdResult.isEmpty mustBe true
      })
    }
  }
}
