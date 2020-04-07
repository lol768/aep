package controllers.admin

import java.io.File

import controllers.admin.AssessmentsController.{AdHocAssessmentFormData, AssessmentFormData}
import domain.Assessment.{AssessmentType, Platform}
import domain.{DepartmentCode, Fixtures}
import helpers.{CleanUpDatabaseAfterEachTest, Scenario}
import play.api.mvc._
import play.api.test.Helpers._
import services.AssessmentService
import specs.BaseSpec
import system.routes.Types.UUID
import warwick.core.helpers.JavaTime
import warwick.sso.User

import scala.concurrent.Future


class AssessmentsControllerTest extends BaseSpec with CleanUpDatabaseAfterEachTest {

  private val appAdminUser = Fixtures.users.admin1
  private val phAdminUser = Fixtures.users.phAdmin
  private val lfAdminUser = Fixtures.users.lfAdmin
  private val someGuy = Fixtures.users.staff1

  private val assessmentService = get[AssessmentService]
  private val file: File = new File(getClass.getResource(Fixtures.uploadedFiles.homeOfficeStatementPDF.path).getFile)

  "AssessmentsController" should {
    "Display the index page to an app admin or departmental admin" in {
      Seq(appAdminUser, phAdminUser).foreach { user =>
        val resIndex = reqIndex(user)
        status(resIndex) mustBe OK
        htmlErrors(resIndex) mustBe empty
      }
    }

    "Not display the index page to a user who isn't in any admin webgroup" in {
      val res = reqIndex(someGuy)
      status(res) mustBe FORBIDDEN
    }

    "Display an assessment to an app admin or a departmental admin for the dept code associated with that assessment" in scenario(new PhilosophyAssessmentScenario()) { s =>
      Seq(appAdminUser, phAdminUser).foreach { user =>
        val resShow = reqShow(user, s.assessmentId)
        status(resShow) mustBe OK
        htmlErrors(resShow) mustBe empty
      }
    }

    "Not display an assessment to a user who isn't an admin for the dept code associated with the assessment" in scenario(new PhilosophyAssessmentScenario()) { s =>
      Seq(lfAdminUser, someGuy).foreach{ user =>
        val resShow = reqShow(user, s.assessmentId)
        status(resShow) mustBe FORBIDDEN
      }
    }

    "Allow app admin or admin for any department to view the create assessment form" in {
      Seq(appAdminUser, phAdminUser, lfAdminUser).foreach { user =>
        val resCreate = reqCreate(user)
        status(resCreate) mustBe OK
        htmlErrors(resCreate) mustBe empty
      }
    }

    "Not allow non-admins to view the create assessment form" in {
      val resCreate = reqCreate(someGuy)
      status(resCreate) mustBe FORBIDDEN
    }

    "Allow an app admin or dept admin for appropriate department to save an assessment" in scenario(new CreatingPhilosophyAssessmentScenario()) { s =>
      Seq(appAdminUser, phAdminUser).foreach { user =>
        val resSave = reqSave(user, s.data)
        status(resSave) mustBe SEE_OTHER
        htmlErrors(resSave) mustBe empty
        header("Location", resSave).value mustBe controllers.admin.routes.AssessmentsController.index().url
      }
    }

    "Not allow non-admin or admin for other department to save an assessment if not correct dept" in scenario(new CreatingPhilosophyAssessmentScenario()) { s =>
      val resSaveLfAdmin = reqSave(lfAdminUser, s.data)
      status(resSaveLfAdmin) mustBe SEE_OTHER
      header("Location", resSaveLfAdmin).value mustBe controllers.admin.routes.AssessmentsController.create().url

      val resSaveSomeGuy = reqSave(someGuy, s.data)
      status(resSaveSomeGuy) mustBe FORBIDDEN
    }

    "Allow app admin or dept admin to update an assessment if dept matches" in scenario(new UpdatingPhilosophyAssessmentScenario()) { s =>
      Seq(appAdminUser, phAdminUser).foreach { user =>
        val resUpdate = reqUpdate(user, s.assessmentId, s.data)
        status(resUpdate) mustBe SEE_OTHER
        header("Location", resUpdate).value mustBe controllers.admin.routes.AssessmentsController.index().url
      }
    }

    "Not allow non-admin or admin for other department to update an assessment if not correct dept" in scenario(new UpdatingPhilosophyAssessmentScenario()) { s =>
      Seq(lfAdminUser, someGuy).foreach { user =>
        val resUpdate = reqUpdate(user, s.assessmentId, s.data)
        status(resUpdate) mustBe FORBIDDEN
      }
    }

  }

  class PhilosophyAssessmentScenario extends Scenario(scenarioCtx) {
    val assessmentId: UUID =
      assessmentService.insert(
        Fixtures.assessments.philosophyAssessment,
        Seq.empty
      ).futureValue.toOption.getOrElse(assessmentCreationError).id
  }

  class CreatingPhilosophyAssessmentScenario extends Scenario(scenarioCtx) {
    val data: AdHocAssessmentFormData = AdHocAssessmentFormData(
      moduleCode = "honk",
      paperCode = "bonk",
      section = None,
      departmentCode = DepartmentCode("ph"),
      sequence = "honk",
      startTime = Some(JavaTime.localDateTime),
      invigilators = Set.empty,
      title = "bonk",
      description = None,
      durationMinutes = Some(120L),
      platform = Platform.OnlineExams,
      assessmentType = AssessmentType.OpenBook,
      url = None,
    )
  }

  class UpdatingPhilosophyAssessmentScenario extends PhilosophyAssessmentScenario {
    private val a = Fixtures.assessments.philosophyAssessment
    val data: AssessmentFormData = AssessmentFormData(
      moduleCode = a.moduleCode,
      paperCode = a.paperCode,
      section = a.section,
      departmentCode = a.departmentCode,
      sequence = a.sequence,
      title = a.title,
      description = Some("Honky bonky binky bang"),
      durationMinutes = a.duration.map(_.toMinutes),
      platform = a.platform,
      assessmentType = a.assessmentType,
      url = Some("https://www.warwick.ac.uk"),
      invigilators = Set.empty,
    )
  }

  private val controllerRoute = controllers.admin.routes.AssessmentsController

  def reqIndex(user: User): Future[Result] =
    req(controllerRoute.index().url).forUser(user).get()

  def reqShow(user: User, assessmentId: UUID): Future[Result] =
    req(controllerRoute.show(assessmentId).url).forUser(user).get()

  def reqCreate(user: User): Future[Result] =
    req(controllerRoute.create().url).forUser(user).get()

  def reqSave(user: User, data: AdHocAssessmentFormData): Future[Result] =
    req(controllerRoute.save().url)
      .forUser(user)
      .withFile(file)
      .postMultiPartForm(tuplesFromAdHocData(data))

  def reqUpdate(user: User, assessmentId: UUID, data: AssessmentFormData): Future[Result] =
    req(controllerRoute.update(assessmentId).url)
      .forUser(user)
      .withFile(file)
      .postMultiPartForm(tuplesFromFormData(data))

  def tuplesFromFormData(data:AssessmentFormData): Seq[(String, String)] =
    Seq(
      "moduleCode" -> data.moduleCode,
      "paperCode" -> data.paperCode,
      "section" -> data.section.getOrElse(""),
      "departmentCode" -> data.departmentCode.lowerCase,
      "sequence" -> data.sequence,
      "title" -> data.title,
      "description" -> data.description.getOrElse(""),
      "durationMinutes" -> data.durationMinutes.map(_.toString).getOrElse(""),
      "platform" -> data.platform.toString,
      "assessmentType" -> data.assessmentType.toString,
      "url" -> data.url.getOrElse(""),
    )

  def tuplesFromAdHocData(data: AdHocAssessmentFormData): Seq[(String, String)] =
    Seq(
      "moduleCode" -> data.moduleCode,
      "paperCode" -> data.paperCode,
      "section" -> data.section.getOrElse(""),
      "departmentCode" -> data.departmentCode.lowerCase,
      "sequence" -> data.sequence,
      "startTime" -> data.startTime.map(_.toString).getOrElse(""),
      "title" -> data.title,
      "description" -> data.description.getOrElse(""),
      "durationMinutes" -> data.durationMinutes.map(_.toString).getOrElse(""),
      "platform" -> data.platform.toString,
      "assessmentType" -> data.assessmentType.toString,
      "url" -> data.url.getOrElse(""),
    ) ++ data.invigilators.zipWithIndex.map { case (invigilator, index) =>
      s"invigilators[$index]" -> invigilator.string
    }.toSeq

  private def assessmentCreationError =
    throw new Exception("Problem creating assessment")
}
