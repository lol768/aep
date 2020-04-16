package controllers.admin

import java.io.File
import java.util.UUID

import controllers.admin.AssessmentsController.AssessmentFormData
import domain.Assessment.{AssessmentType, DurationStyle, Platform}
import domain.{DepartmentCode, Fixtures}
import helpers.{CleanUpDatabaseAfterEachTest, Scenario}
import play.api.mvc._
import play.api.test.Helpers._
import services.AssessmentService
import specs.BaseSpec
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
        val resShow = reqUpdateForm(user, s.assessmentId)
        status(resShow) mustBe OK
        htmlErrors(resShow) mustBe empty
      }
    }

    "Not display an assessment to a user who isn't an admin for the dept code associated with the assessment" in scenario(new PhilosophyAssessmentScenario()) { s =>
      Seq(lfAdminUser, someGuy).foreach{ user =>
        val resShow = reqUpdateForm(user, s.assessmentId)
        status(resShow) mustBe FORBIDDEN
      }
    }

    "Allow app admin or admin for any department to view the create assessment form" in {
      Seq(appAdminUser, phAdminUser, lfAdminUser).foreach { user =>
        val resCreate = reqCreateForm(user)
        status(resCreate) mustBe OK
        htmlErrors(resCreate) mustBe empty
      }
    }

    "Not allow non-admins to view the create assessment form" in {
      val resCreate = reqCreateForm(someGuy)
      status(resCreate) mustBe FORBIDDEN
    }

    "Allow an app admin or dept admin for appropriate department to save an assessment" in scenario(new CreatingPhilosophyAssessmentScenario()) { s =>
      Seq(appAdminUser, phAdminUser).foreach { user =>
        val resSave = reqCreate(user, s.data)
        status(resSave) mustBe SEE_OTHER
        htmlErrors(resSave) mustBe empty
        header("Location", resSave).value mustBe controllers.admin.routes.AssessmentsController.index().url
      }
    }

    "Not allow non-admin or admin for other department to save an assessment if not correct dept" in scenario(new CreatingPhilosophyAssessmentScenario()) { s =>
      val resSaveLfAdmin = reqCreate(lfAdminUser, s.data)
      status(resSaveLfAdmin) mustBe SEE_OTHER
      header("Location", resSaveLfAdmin).value mustBe controllers.admin.routes.AssessmentsController.create().url

      val resSaveSomeGuy = reqCreate(someGuy, s.data)
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

    "Properly validate cases where no platform has been selected during creation" in scenario(new NoPlatformDataCreateScenario()) { s =>
      val resCreate = reqCreate(phAdminUser, s.badData)
      status(resCreate) mustBe OK
      htmlErrors(resCreate) mustNot be(empty)
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
    val data: AssessmentFormData = AssessmentFormData(
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
      durationStyle = DurationStyle.DayWindow,
      platform = Set(Platform.OnlineExams),
      assessmentType = Some(AssessmentType.OpenBook),
      students = Set.empty,
      urls = Map.empty,
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
      startTime = a.startTime.map(_.toLocalDateTime),
      title = a.title,
      description = Some("Honky bonky binky bang"),
      durationMinutes = a.duration.map(_.toMinutes),
      durationStyle = DurationStyle.DayWindow,
      platform = a.platform,
      assessmentType = a.assessmentType,
      urls = a.platform.map(p => p -> "https://www.warwick.ac.uk").toMap,
      students = Set.empty,
      invigilators = Set.empty,
    )
  }

  class NoPlatformDataCreateScenario extends CreatingPhilosophyAssessmentScenario {
    val badData: AssessmentFormData = data.copy(platform = Set.empty)
  }

  private val controllerRoute = controllers.admin.routes.AssessmentsController

  def reqIndex(user: User): Future[Result] =
    req(controllerRoute.index().url).forUser(user).get()

  def reqUpdateForm(user: User, assessmentId: UUID): Future[Result] =
    req(controllerRoute.updateForm(assessmentId).url).forUser(user).get()

  def reqCreateForm(user: User): Future[Result] =
    req(controllerRoute.createForm().url).forUser(user).get()

  def reqCreate(user: User, data: AssessmentFormData): Future[Result] =
    req(controllerRoute.create().url)
      .forUser(user)
      .withFile(file)
      .postMultiPartForm(tuplesFromFormData(data))

  def reqUpdate(user: User, assessmentId: UUID, data: AssessmentFormData): Future[Result] =
    req(controllerRoute.update(assessmentId).url)
      .forUser(user)
      .withFile(file)
      .postMultiPartForm(tuplesFromFormData(data))

  def tuplesFromFormData(data: AssessmentFormData): Seq[(String, String)] =
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
      "platform[]" -> data.platform.mkString(","),
      "assessmentType" -> data.assessmentType.map(_.toString).getOrElse(""),
      "students" -> data.students.toSeq.map(_.string).sorted.mkString("\n"),
    ) ++ data.urls.map { case (platform, url) =>
      s"urls.${platform.entryName}" -> url
    } ++ data.invigilators.zipWithIndex.map { case (invigilator, index) =>
      s"invigilators[$index]" -> invigilator.string
    }.toSeq

  private def assessmentCreationError =
    throw new Exception("Problem creating assessment")
}
