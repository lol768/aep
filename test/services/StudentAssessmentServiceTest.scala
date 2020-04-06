package services

import java.time.Duration

import domain.{Fixtures, StudentAssessment}
import domain.Fixtures.uploadedFiles.{homeOfficeStatementPDF, specialJPG}
import domain.dao.{AbstractDaoTest, AssessmentDao, StudentAssessmentDao}
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.sso.{UniversityID, Usercode}

class StudentAssessmentServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val service = get[StudentAssessmentService]

  private trait Fixture {
    private val assessmentDao = get[AssessmentDao]
    private val studentAssessmentDao = get[StudentAssessmentDao]

    // Set up some test assessments
    val storedAssessment = Fixtures.assessments.storedAssessment().copy(startTime = Some(JavaTime.offsetDateTime.minusHours(1)))
    execWithCommit(DBIO.sequence(Seq(storedAssessment).map(assessmentDao.insert)))

    val storedStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("1234567"))
    execWithCommit(DBIO.sequence(Seq(storedStudentAssessment).map(studentAssessmentDao.insert)))
  }

  "StudentAssessmentService" should {
    "inflate UploadedFiles in the same order as submitted" in new Fixture {
      // Add some files to studentAssessment
      val file1 = (specialJPG.temporaryUploadedFile.in, specialJPG.uploadedFileSave)
      val file2 = (homeOfficeStatementPDF.temporaryUploadedFile.in, homeOfficeStatementPDF.uploadedFileSave)

      val base = service.getWithAssessment(storedStudentAssessment.studentId, storedStudentAssessment.assessmentId).serviceValue.get

      val started = service.startAssessment(base.studentAssessment).serviceValue

      val updated = service.attachFilesToAssessment(started, Seq(file1, file2)).serviceValue
      updated.uploadedFiles.map(_.fileName) mustBe Seq(file1, file2).map(_._2.fileName)

      val updated2 = service.attachFilesToAssessment(updated, Seq(file2, file1)).serviceValue
      updated2.uploadedFiles.map(_.fileName) mustBe Seq(file1, file2, file2, file1).map(_._2.fileName)
    }

    "get, insert and update a student assessment" in new Fixture {
      val newStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("HONK"))

      service.upsert(newStudentAssessment.asStudentAssessment(Map.empty)).serviceValue

      val studentAssessmentFromDB = service.get(newStudentAssessment.studentId, storedAssessment.id).serviceValue
        .getOrElse(throw new Exception("Problem getting student assessment from DB"))

      val finaliseTime = JavaTime.offsetDateTime
      val updatedStudentAssessment = studentAssessmentFromDB.copy(
        finaliseTime = Some(finaliseTime)
      )

      service.upsert(updatedStudentAssessment).serviceValue

      service.get(newStudentAssessment.studentId, storedAssessment.id).serviceValue
        .getOrElse(throw new Exception("Problem getting student assessment from DB"))
        .finaliseTime mustBe Some(finaliseTime)
    }

    "prevent starting before earliest allowed start time" in new Fixture {
      // startTime is set to 1 hour before current time
      val early = JavaTime.instant.minus(Duration.ofHours(7))

      DateTimeUtils.useMockDateTime(early, () => {
        val sawa = service.getWithAssessment(storedStudentAssessment.studentId, storedAssessment.id).serviceValue.value
        val caught = intercept[IllegalArgumentException] {
          service.startAssessment(sawa.studentAssessment).serviceValue
        }
        caught.getMessage must include("too early")
      })
    }

    "can start inside allowed start range" in new Fixture {
      // startTime is set to 1 hour before current time so we can use real time
      val sawa = service.getWithAssessment(storedStudentAssessment.studentId, storedAssessment.id).serviceValue.value
      val studentAssessment: StudentAssessment = service.startAssessment(sawa.studentAssessment).serviceValue
      studentAssessment.startTime mustBe defined
    }

    "prevent starting after last allowed start time" in new Fixture {
      val veryLate = JavaTime.instant.plus(Duration.ofHours(25))

      DateTimeUtils.useMockDateTime(veryLate, () => {
        val sawa = service.getWithAssessment(storedStudentAssessment.studentId, storedAssessment.id).serviceValue.value
        val caught = intercept[IllegalArgumentException] {
          service.startAssessment(sawa.studentAssessment).serviceValue
        }
        caught.getMessage must include("too late")
      })

      "get, insert and update declarations" in new Fixture {
        val newStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("HONK"))
        val newDeclarations = Fixtures.studentAssessments.storedDeclarations(newStudentAssessment.id)

        service.upsert(newDeclarations.asDeclarations).serviceValue

        val declarationsFromDB = service.getDeclarations(newDeclarations.id).serviceValue
        declarationsFromDB.acceptable mustBe true

        val finaliseTime = JavaTime.offsetDateTime
        val updatedDeclarations = declarationsFromDB.copy(
          selfDeclaredRA = true
        )

        service.upsert(updatedDeclarations).serviceValue

        service.getDeclarations(newDeclarations.id).serviceValue.selfDeclaredRA mustBe true
      }
    }
  }
}
