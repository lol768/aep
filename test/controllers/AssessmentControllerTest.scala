package controllers

import java.io.File
import java.time.Duration
import java.util.UUID

import controllers.admin.AdminAssessmentsController.AssessmentFormData
import domain.Assessment.{AssessmentType, Platform}
import domain.dao.{AssessmentDao, StudentAssessmentDao}
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.{StoredDeclarations, StoredStudentAssessment}
import domain.{Assessment, Declarations, DepartmentCode, Fixtures, Sitting, StudentAssessment}
import helpers.{CleanUpDatabaseAfterEachTest, Scenario, SimpleSemanticRelativeTime}
import play.api.mvc._
import play.api.test.Helpers._
import services.{AssessmentService, StudentAssessmentService}
import specs.BaseSpec
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, User}

import scala.concurrent.Future

class AssessmentControllerTest extends BaseSpec with CleanUpDatabaseAfterEachTest with SimpleSemanticRelativeTime {

  private val assessmentDao = get[AssessmentDao]
  private val studentAssessmentDao = get[StudentAssessmentDao]
  private val assessmentService = get[AssessmentService]
  private val studentAssessmentService = get[StudentAssessmentService]

  "AssessmentController" should {
    "Allow a student to view the assessment they have scheduled" in new AssessmentNotStartedScenario() { s =>
      private val resView = reqView(s.TheAssessment, s.Rupert)
      status(resView) mustBe OK
      htmlErrors(resView) mustBe empty
    }

    "Not allow a student to view an assessment that they're not scheduled to take" in new AssessmentNotStartedScenario() { s =>
      private val resView = reqView(s.TheAssessment, s.Herbert)
      status(resView) mustBe NOT_FOUND
    }

    "Show the authorship declaration form if it has not been accepted already" in new AssessmentNotStartedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include("<h1>Declaration and statement of authorship</h1>")
    }

    "Not allow a student to start an assessment that they're not scheduled to take" in new AssessmentNotStartedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Herbert)
      status(resStart) mustBe NOT_FOUND
    }

    "Show the reasonable adjustments form if it has not been declared yet" in new OnlyAuthorshipDeclarationAcceptedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe OK
      contentAsString(resStart) must include("<h1>Reasonable Adjustments</h1>")
    }

    "Redirect the student to the assessment if all declarations have been accepted" in new AllDeclarationsAcceptedScenario() { s =>
      private val resStart = reqStart(s.TheAssessment, s.Rupert)
      status(resStart) mustBe SEE_OTHER
      header("Location", resStart).value mustBe controllers.routes.AssessmentController.view(s.TheAssessment.id).url
    }
  }

  class BasicSittingScenario extends Scenario(scenarioCtx) {
    val assessmentId: UUID = UUID.randomUUID
    private val storedAssessment: StoredAssessment =
      Fixtures.assessments.storedAssessment(
        uuid = assessmentId,
        platformOption = Some(Platform.OnlineExams),
        duration = Some(Duration.ofHours(3L))
      ).copy(
        startTime = Some(2.hours ago)
      )
      execWithCommit(assessmentDao.insert(storedAssessment))

    // Rupert Hampole is sitting his assessment today
    val Rupert: User = Fixtures.users.student1
    val RupertsId: UniversityID = Rupert.universityId.get

    // Herbert Crest is a naughty student who wants to look at Rupert's exam!
    val Herbert: User = Fixtures.users.student2

    val TheAssessment: Assessment = assessmentService.get(assessmentId).futureValue.toOption.get
  }

  class AssessmentNotStartedScenario extends BasicSittingScenario {
    private val storedStudentAssessment: StoredStudentAssessment =
      Fixtures.studentAssessments.storedStudentAssessment(
        assessmentId,
        RupertsId
      )
    execWithCommit(studentAssessmentDao.insert(storedStudentAssessment))
    val RupertsAssessment: StudentAssessment = studentAssessmentService.get(RupertsId, assessmentId).futureValue.toOption.get
  }

  class OnlyAuthorshipDeclarationAcceptedScenario extends AssessmentNotStartedScenario {
    private val declarations = Declarations(
      RupertsAssessment.id,
      acceptsAuthorship = true,
    )
    studentAssessmentService.upsert(declarations).futureValue
  }

  class AllDeclarationsAcceptedScenario extends AssessmentNotStartedScenario {
    private val declarations = Declarations(
      RupertsAssessment.id,
      acceptsAuthorship = true,
      completedRA = true,
    )
    studentAssessmentService.upsert(declarations).futureValue
  }

  def reqView(assessment: Assessment, user: User): Future[Result] =
    req(controllers.routes.AssessmentController.view(assessment.id).url)
      .forUser(user)
      .get()

  def reqStart(assessment: Assessment, user: User): Future[Result] =
    req(controllers.routes.AssessmentController.start(assessment.id).url)
      .forUser(user)
      .post(Seq.empty)

}
