package services

import domain.dao.AbstractDaoTest
import helpers.CleanUpDatabaseAfterEachTest
import org.mockito.Mockito.reset
import system.BindingOverrides
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

class DataGenerationServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val dataGenerationService = get[DataGenerationService]
  private lazy val assessmentService = get[AssessmentService]
  private lazy val studentAssessmentService = get[StudentAssessmentService]

  override def afterEach(): Unit = {
    super.afterEach()

    // Reset the RNG back to how it would be at the start of the test
    dataGeneration.random.setSeed(BindingOverrides.fixedRandomSeed)
  }

  "DataGenerationService" should {
    "Create the requested number of randomly generated assessments in the database" in {
      dataGenerationService.putRandomAssessmentsInDatabase(2).serviceValue
      assessmentService.list.serviceValue.length mustBe 2
    }

    "Create the requested number of randomly generated assessments and associated student assessments in the database" in {
      dataGenerationService.putRandomAssessmentsWithStudentAssessmentsInDatabase(5).serviceValue

      val assessmentData = assessmentService.list.serviceValue
      assessmentData.length mustBe 5

      val studentAssessmentData = studentAssessmentService.list.serviceValue
      (studentAssessmentData.length % dataGenerationService.numberOfIds) mustBe 0 // Should be exact multiple of number of assessments created
      studentAssessmentData.map(_.assessmentId).foreach { id =>
        assessmentData.map(_.id) must contain(id)
      }
    }
  }

}
