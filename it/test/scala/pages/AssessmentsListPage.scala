package pages

import org.openqa.selenium.{By, WebDriver, WebElement}
import support.ServerInfo

import scala.jdk.CollectionConverters._

case class AssessmentInfo(title: String, viewLink: WebElement)

class AssessmentsListPage()(implicit driver: WebDriver, server: ServerInfo) extends AbstractPage("Assessments List", driver, server) {
  override val path: String = "/assessments"

  def assessmentsList: Seq[AssessmentInfo] = driver.findElements(By.className("assessment-information-panel")).asScala.filter { div =>
    div.findElements(By.tagName("a")).asScala.nonEmpty
  }.map { div =>
      val title = div.findElements(By.tagName("h4")).asScala.head.getText
      val viewLink = div.findElements(By.tagName("a")).asScala.head
      AssessmentInfo(title, viewLink)
    }.toSeq

}
