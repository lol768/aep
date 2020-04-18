package services

import java.util.UUID

import domain.Fixtures
import domain.Fixtures.uploadedFiles.{homeOfficeStatementPDF, specialJPG}
import domain.dao.{AbstractDaoTest, AssessmentDao}
import helpers.CleanUpDatabaseAfterEachTest
import play.api.libs.Files.TemporaryFileCreator
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

class AssessmentServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val service = get[AssessmentService]
  private implicit lazy val temporaryFileCreator: TemporaryFileCreator = get[TemporaryFileCreator]

  private trait Fixture {
    private val dao = get[AssessmentDao]

    // Set up some test assessments
    val storedAssessment1 = Fixtures.assessments.storedAssessment()
    val storedAssessment2 = Fixtures.assessments.storedAssessment()

    execWithCommit(DBIO.sequence(Seq(storedAssessment1, storedAssessment2).map(dao.insert)))
  }

  "AssessmentService" should {
    "inflate UploadedFiles in the same order as the brief" in new Fixture {
      // Add some files to assessment1
      val file1 = (specialJPG.temporaryUploadedFile.in, specialJPG.uploadedFileSave)
      val file2 = (homeOfficeStatementPDF.temporaryUploadedFile.in, homeOfficeStatementPDF.uploadedFileSave)

      val base = service.get(storedAssessment1.id).serviceValue

      val updated = service.update(base, Seq(file1, file2)).serviceValue
      updated.brief.files.map(_.fileName) mustBe Seq(file1, file2).map(_._2.fileName)

      val updated2 = service.update(updated, Seq(file2, file1)).serviceValue
      updated2.brief.files.map(_.fileName) mustBe Seq(file2, file1).map(_._2.fileName)
    }

    "insert, get and update an assessment" in {
      val assessmentId = UUID.randomUUID
      val storedAssessment = Fixtures.assessments.storedAssessment(assessmentId)

      service.upsert(storedAssessment.asAssessment(Map.empty)).serviceValue
      val assessmentFromDB = service.get(assessmentId).serviceValue

      assessmentFromDB.title mustBe storedAssessment.title
      val newTitle = "Online Assessment: Workingfulness and Functioningality in Web Applications"
      val updatedAssessment = assessmentFromDB.copy(
        title = newTitle
      )

      service.upsert(updatedAssessment).serviceValue

      service.get(assessmentId).serviceValue.title mustBe newTitle
    }
  }

}
