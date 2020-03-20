package pages

import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatestplus.selenium._
import support.ServerInfo
import warwick.html.SeleniumID7

import scala.jdk.CollectionConverters._

case class ApplicationList(title: String, items: Seq[ApplicationListItem])
case class ApplicationListItem(link: WebElement, withdrawLink: Option[WebElement])

abstract class AbstractPage(val name: String, driver: WebDriver, server: ServerInfo) extends Page {
  override lazy val url: String = server.baseUrl + path
  def path: String

  val browser = new WebBrowser with SeleniumID7
}

class HomePage()(implicit driver: WebDriver, server: ServerInfo) extends AbstractPage("Home", driver, server) {
  override val path: String = "/"
}
