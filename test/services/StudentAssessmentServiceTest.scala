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

    val storedAssessment = Fixtures.assessments.storedAssessment().copy(startTime = Some(JavaTime.offsetDateTime.minusHours(1)))
    execWithCommit(assessmentDao.insert(storedAssessment))

    val storedStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("1234567"))
    val storedGoodDeclarations = Fixtures.studentAssessments.storedDeclarations(storedStudentAssessment.id)
    execWithCommit(studentAssessmentDao.insert(storedStudentAssessment) andThen studentAssessmentDao.insert(storedGoodDeclarations))

    val storedStudentAssessmentNoAuthorship = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("0000001"))
    val storedDeclarationsNoAuthorship = Fixtures.studentAssessments.storedDeclarations(storedStudentAssessmentNoAuthorship.id).copy(acceptsAuthorship = false)
    execWithCommit(studentAssessmentDao.insert(storedStudentAssessmentNoAuthorship) andThen studentAssessmentDao.insert(storedDeclarationsNoAuthorship))

    val storedStudentAssessmentNoRA = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("9876543"))
    val storedDeclarationsNoRA = Fixtures.studentAssessments.storedDeclarations(storedStudentAssessmentNoRA.id).copy(completedRA = false)
    execWithCommit(studentAssessmentDao.insert(storedStudentAssessmentNoRA) andThen studentAssessmentDao.insert(storedDeclarationsNoRA))
  }

  "StudentAssessmentService" should {
    "validate declarations" in new Fixture {
      val noAuthorshipSitting = service.getSitting(storedStudentAssessmentNoAuthorship.studentId, storedStudentAssessmentNoAuthorship.assessmentId).serviceValue.get
      val noAuthorshipCaught = intercept[IllegalArgumentException] {
        service.startAssessment(noAuthorshipSitting.studentAssessment).serviceValue
      }
      noAuthorshipCaught.getMessage must include("declarations not made")

      val noRASitting = service.getSitting(storedStudentAssessmentNoRA.studentId, storedStudentAssessmentNoRA.assessmentId).serviceValue.get
      val noRACaught = intercept[IllegalArgumentException] {
        service.startAssessment(noRASitting.studentAssessment).serviceValue
      }
      noRACaught.getMessage must include("declarations not made")

      val goodSitting = service.getSitting(storedStudentAssessment.studentId, storedStudentAssessment.assessmentId).serviceValue.get
      service.startAssessment(goodSitting.studentAssessment).serviceValue
    }

    "inflate UploadedFiles in the same order as submitted" in new Fixture {
      // Add some files to studentAssessment
      val file1 = (specialJPG.temporaryUploadedFile.in, specialJPG.uploadedFileSave)
      val file2 = (homeOfficeStatementPDF.temporaryUploadedFile.in, homeOfficeStatementPDF.uploadedFileSave)

      val base = service.getSitting(storedStudentAssessment.studentId, storedStudentAssessment.assessmentId).serviceValue.get

      val started = service.startAssessment(base.studentAssessment).serviceValue

      val updated = service.attachFilesToAssessment(started, Seq(file1, file2)).serviceValue
      updated.uploadedFiles.map(_.fileName) mustBe Seq(file1, file2).map(_._2.fileName)

      val updated2 = service.attachFilesToAssessment(updated, Seq(file2, file1)).serviceValue
      updated2.uploadedFiles.map(_.fileName) mustBe Seq(file1, file2, file2, file1).map(_._2.fileName)
    }

    "get, insert and update a student assessment" in new Fixture {
      val newStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("0000007"))

      service.upsert(newStudentAssessment.asStudentAssessment(Map.empty)).serviceValue

      val studentAssessmentFromDB = service.get(newStudentAssessment.studentId, storedAssessment.id).serviceValue

      val finaliseTime = JavaTime.offsetDateTime
      val updatedStudentAssessment = studentAssessmentFromDB.copy(
        finaliseTime = Some(finaliseTime)
      )

      service.upsert(updatedStudentAssessment).serviceValue

      service.get(newStudentAssessment.studentId, storedAssessment.id).serviceValue
        .finaliseTime mustBe Some(finaliseTime)
    }

    "prevent starting before earliest allowed start time" in new Fixture {
      // startTime is set to 1 hour before current time
      val early = JavaTime.instant.minus(Duration.ofHours(7))

      DateTimeUtils.useMockDateTime(early, () => {
        val sitting = service.getSitting(storedStudentAssessment.studentId, storedAssessment.id).serviceValue.value
        val caught = intercept[IllegalArgumentException] {
          service.startAssessment(sitting.studentAssessment).serviceValue
        }
        caught.getMessage must include("too early")
      })
    }

    "can start inside allowed start range" in new Fixture {
      // startTime is set to 1 hour before current time so we can use real time
      val sitting = service.getSitting(storedStudentAssessment.studentId, storedAssessment.id).serviceValue.value
      val studentAssessment: StudentAssessment = service.startAssessment(sitting.studentAssessment).serviceValue
      studentAssessment.startTime mustBe defined
    }

    "prevent starting after last allowed start time" in new Fixture {
      val veryLate = JavaTime.instant.plus(Duration.ofHours(25))

      DateTimeUtils.useMockDateTime(veryLate, () => {
        val sitting = service.getSitting(storedStudentAssessment.studentId, storedAssessment.id).serviceValue.value
        val caught = intercept[IllegalArgumentException] {
          service.startAssessment(sitting.studentAssessment).serviceValue
        }
        caught.getMessage must include("too late")
      })
    }

    "get, insert and update declarations" in new Fixture {
      val newStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("0000007"))
      val newDeclarations = Fixtures.studentAssessments.storedDeclarations(newStudentAssessment.id)

      service.upsert(newDeclarations.asDeclarations).serviceValue

      val declarationsFromDB = service.getOrDefaultDeclarations(newDeclarations.studentAssessmentId).serviceValue
      declarationsFromDB.acceptable mustBe true

      val finaliseTime = JavaTime.offsetDateTime
      val updatedDeclarations = declarationsFromDB.copy(
        selfDeclaredRA = true
      )

      service.upsert(updatedDeclarations).serviceValue

      service.getOrDefaultDeclarations(newDeclarations.studentAssessmentId).serviceValue.selfDeclaredRA mustBe true
    }
  }
}
