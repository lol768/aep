package services

import java.time.ZonedDateTime

import domain.Fixtures
import domain.Fixtures.uploadedFiles.{homeOfficeStatementPDF, specialJPG}
import domain.dao.{AbstractDaoTest, AssessmentDao}
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

class AssessmentServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val service = get[AssessmentService]

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

      val updated2 = service.update(base, Seq(file2, file1)).serviceValue
      updated2.brief.files.map(_.fileName) mustBe Seq(file2, file1).map(_._2.fileName)
    }
  }

}
