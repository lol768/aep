package support

import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import scala.jdk.CollectionConverters._

import domain.Assessment.Platform.OnlineExams
import domain.Fixtures.{assessments, studentAssessments}
import domain.dao.AssessmentsTables.StoredBrief
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunning, StudentAssessmentDao}
import domain.{Fixtures, UploadedFileOwner}
import helpers.{FutureServiceMixins, HasApplicationGet}
import org.apache.commons.text.StringEscapeUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import org.scalatestplus.selenium.{Page, WebBrowser}
import pages.{AbstractPage, AssessmentPage, AssessmentsListPage, HomePage}
import play.api.db.DBApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.{Langs, MessagesApi, MessagesImpl, MessagesProvider}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Call, Result}
import services._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import warwick.functional._
import warwick.html._
import warwick.sso._

import scala.concurrent.Future

case class ServerInfo(
  port: Int,
  baseUrl: String
)

case class HtmlSnippet(name: String, value: String)

abstract class BrowserFeatureSpec extends AbstractFunctionalTest
  with DaoRunning
  with WebBrowser
  with GivenWhenThen
  with EmbeddedPostgres
  with FutureServiceMixins
  with ScalaFutures
  with SeleniumID7
  with BeforeAndAfterEach
  with HasApplicationGet
  with FailureScreenshots
  with NoAuditLogging {

  protected lazy val dbConfigProvider: DatabaseConfigProvider = get[DatabaseConfigProvider]
  lazy val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  private val assessmentDao = get[AssessmentDao]
  private val studentAssessmentDao = get[StudentAssessmentDao]
  private val uploadedFileService = get[UploadedFileService]

  override def fakeApplicationBuilder: GuiceApplicationBuilder = super.fakeApplicationBuilder
    .overrides(
      bind[LoginContext].toProvider[MockLoginContextProvider]
    )

  /** Transfer defaultUser into MockLoginContextProvider so that it's visible to
    * MockSSOClientHandler.
    *
    * This should get absorbed up into AbstractFunctionalTest so that dynamic user switching
    * is built in.
    */
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    webDriver.manage().window().setSize(targetWindowSize)
    setUser(defaultUsercode)
  }

  lazy val mockUsers: Seq[User] = MockUserLookupService.getUsers(mockUserConfig)
  def getMockUser(usercode: Usercode): Option[User] = mockUsers.find(_.usercode == usercode)

  def setUser(usercode: Option[Usercode]): Unit = {
    val user = usercode.map(u => getMockUser(u).getOrElse(throw new IllegalArgumentException(s"$u not defined as a mock user")))
    get[MockLoginContextProvider].set(new MockLoginContext(user))
  }

  def setUser(usercode: Usercode): Unit = setUser(Some(usercode))
  def setUser(user: User): Unit = setUser(Some(user.usercode))

  override val screenshotDirectory: File = new File("it/target/screenshots")

  implicit lazy val messagesProvider: MessagesProvider = MessagesImpl(get[Langs].availables.head, get[MessagesApi])

  lazy val databaseApi: DBApi = get[DBApi]

  var res: Future[Result] = _

  val targetWindowSize = new org.openqa.selenium.Dimension(1024, 768 * 2)

  lazy val baseHost = s"localhost:$port"
  lazy val baseUrl = s"http://$baseHost"

  // gets passed in to Page instances
  implicit val serverInfo: ServerInfo = ServerInfo(port, baseUrl)

  def urlTo(call: Call): String = call.absoluteURL(false, s"localhost:$port")

  val homePage = new HomePage()
  val assessmentsListPage = new AssessmentsListPage()
  var assessmentPage: AssessmentPage = null

  def visit(path: String): Unit = {
    go to (baseUrl + path)
  }

  def visit(call: Call): Unit = {
    go to urlTo(call)
  }

  def visit(page: Page): Unit = {
    go to page.url
  }

  def pageText: String = find(tagName("body")).get.text

  def assertNotOnLoginPage(): Unit = {
    if (currentUrl startsWith "https://sso.example.com")
      fail("We are on a login page")
  }

  def pageMustContain(text: String, description: String = null): Unit = {
    assert(pageSource.contains(text) || pageText.contains(text), s"Page didn't contain '${Option(description).getOrElse(text)}'")
  }

  def currentUrlMustContain(text: String): Unit = {
    assert(currentUrl.contains(text), s"Current url didn't contain '$text'")
  }

  def pageContentMustContain(text: String): Unit = {
    assertNotOnLoginPage()
    assert(mainContentElement.underlying.getText contains text, s"Page content didn't contain '$text'")
  }

  def pageContentMustNotContain(text: String): Unit = {
    assertNotOnLoginPage()
    assert(!(mainContentElement.underlying.getText contains text), s"Page content contained '$text'")
  }


  def mustRedirectToWebsignon(): Unit = {
    currentUrl must startWith ("https://sso.example.com/login")
  }

  object steps {

    def i_should_see(html: HtmlSnippet): Unit = {
      Then(s"I should see ${html.name}")
      pageMustContain(html.value, html.name)
    }

    def i_visit_the(path: String): Unit = {
      When(s"I visit the $path page")
      visit(path)
    }

    def i_visit_the(page: AbstractPage): Unit = {
      When(s"I visit the ${page.name} page")
      visit(page)
    }

    def i_should_see_the_text(text: String): Unit = {
      Then(s"I should see the text '${unescape(text)}'")
      eventually(pageMustContain(text))
    }

    def the_page_content_should_contain(text: String): Unit = {
      Then(s"I should see in the content the text '${unescape(text)}'")
      pageContentMustContain(text)
    }

    def the_page_content_should_not_contain(text: String): Unit = {
      Then(s"I should not see in the content the text '${unescape(text)}'")
      pageContentMustNotContain(text)
    }

    def the_page_should_contain(text: String): Unit = {
      Then(s"I should see in the page the text '${unescape(text)}'")
      pageMustContain(text)
    }

    def i_am_a_student(): Unit = {
      Given("I am a student")
      setUser(Fixtures.users.student1)
    }

    def i_have_an_online_exam_to_sit(student: User): Unit = {
      Given("I have an Online Exam to sit")

      val assessmentId: UUID = UUID.randomUUID()
      val assessment: AssessmentsTables.StoredAssessment = assessments.storedAssessment(platformOption = Some(OnlineExams)).copy(id = assessmentId, startTime = Some(OffsetDateTime.now))
      val studentAssessment = studentAssessments.storedStudentAssessment(assessment.id, student.universityId.get)

      val examPaper = execWithCommit(uploadedFileService.storeDBIO(Fixtures.uploadedFiles.specialJPG.temporaryUploadedFile.in, Fixtures.uploadedFiles.specialJPG.uploadedFileSave.copy(fileName = "Exam paper.pdf"), Usercode("thisisfine"), assessment.id, UploadedFileOwner.Assessment))
      execWithCommit(assessmentDao.insert(assessment.copy(storedBrief = StoredBrief(text = None, url = None, fileIds = Seq(examPaper.id)))))
      execWithCommit(studentAssessmentDao.insert(studentAssessment))

      assessmentPage = new AssessmentPage(assessment.id)
    }

    def i_have_an_assessment_in_progress(student: User): Unit = {
      Given("I have an assessment in progress")

      val assessmentId: UUID = UUID.randomUUID()
      val assessment: AssessmentsTables.StoredAssessment = assessments.storedAssessment(platformOption = Some(OnlineExams)).copy(id = assessmentId)
      val studentAssessment = studentAssessments.storedStudentAssessment(assessment.id, student.universityId.get).copy(
        startTime = Some(OffsetDateTime.now().minusMinutes(5))
      )

      val examPaper = execWithCommit(uploadedFileService.storeDBIO(Fixtures.uploadedFiles.specialJPG.temporaryUploadedFile.in, Fixtures.uploadedFiles.specialJPG.uploadedFileSave.copy(fileName = "Exam paper.pdf"), Usercode("thisisfine"), assessment.id, UploadedFileOwner.Assessment))
      execWithCommit(assessmentDao.insert(assessment.copy(storedBrief = StoredBrief(text = None, url = None, fileIds = Seq(examPaper.id)))))
      execWithCommit(studentAssessmentDao.insert(studentAssessment))

      assessmentPage = new AssessmentPage(assessment.id)
    }

    def i_click_through_to_my_first_exam_from_assessments_list(): Unit = {
      When("I click through to my exam from the assessment list")
      visit(assessmentsListPage)
      val assessmentList = assessmentsListPage.assessmentsList
      assessmentList.head.viewLink.click()
    }

    def i_start_the_assessment(): Unit = {
      When("I start the assessment")
      assessmentPage.startButton.click()
    }

    def i_upload(filename: String): Unit = {
      When(s"I upload '$filename'")
      val file = new File(s"test/resources/$filename")
      assessmentPage.fileInput.sendKeys(file.getAbsolutePath)
      assessmentPage.uploadFilesButton.click()
    }

    def i_delete(filename: String): Unit = {
      val file = assessmentPage.uploadedFiles.find(_.name == filename)
      assert(file.nonEmpty, s"There was no uploaded file named $filename")
      file.get.deleteButton.click()
    }

    def i_should_see_uploaded_file(filename: String): Unit = {
      Then(s"I should see an uploaded file named '$filename'")
      eventually(assert(assessmentPage.uploadedFiles.map(_.name).contains(filename), s"There was no uploaded file named $filename"))
    }

    def i_should_see_assessment_file(filename: String): Unit = {
      Then(s"I should see an assessment file named '$filename'")
      eventually(assert(assessmentPage.assessmentFiles.map(_.name).contains(filename), s"There was no assessment file named $filename"))
    }

    def i_download_assessment_file(filename: String): Unit = {
      val file = assessmentPage.assessmentFiles.find(_.name == filename)
      assert(file.nonEmpty, s"There was no assessment file named $filename")
      file.get.link.click()
    }

    def a_file_should_be_displayed(): Unit = {
      assert(webDriver.getWindowHandles.size() == 2, s"Expected file download to open in a second tab, but ${webDriver.getWindowHandles.size} tab(s) were open")
      val fileTabHandle = webDriver.getWindowHandles.asScala.diff(Set(webDriver.getWindowHandle)).head
      webDriver.switchTo().window(fileTabHandle)
      assert(webDriver.getTitle.take(36).matches(".{8}-.{4}-.{4}-.{4}-.{12}"), s"Expected the page title to look like a file UUID but was '${webDriver.getTitle}'")
    }

    def i_finish_the_assessment(): Unit = {
      assessmentPage.finishExamCheckbox.click()
      assessmentPage.finishExamButton.submit()
    }

    def i_should_be_redirected_to_exam_page(): Unit = {
      Then("I should be redirected to the exam page")
      eventually(currentUrlMustContain(s"/assessment/"))
    }
  }

  val And: steps.type = steps
  val When: steps.type = steps
  val Given: steps.type = steps
  val Then: steps.type = steps

  // For readability in report output.
  private def unescape(text: String): String = StringEscapeUtils.unescapeHtml4(text)

  def screenshot(description: String): Unit = {
    setCaptureDir(screenshotDirectory.getPath)
    if (isScreenshotSupported) {
      capture to description
    }
  }
}
