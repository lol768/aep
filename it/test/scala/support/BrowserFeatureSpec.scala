package support

import java.io.File

import helpers.{FutureServiceMixins, HasApplicationGet}
import org.apache.commons.text.StringEscapeUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import org.scalatestplus.selenium.{Page, WebBrowser}
import pages.{AbstractPage, HomePage}
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
      pageMustContain(text)
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
