package services

import java.util.UUID

import domain.Fixtures
import domain.Fixtures.uploadedFiles.{homeOfficeStatementPDF, specialJPG}
import domain.dao.{AbstractDaoTest, AssessmentDao, StudentAssessmentDao}
import helpers.CleanUpDatabaseAfterEachTest
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

  "AssessmentService" should {
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
  }

}
